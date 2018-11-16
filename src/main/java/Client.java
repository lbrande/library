import io.lettuce.core.Range;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.HashMap;
import java.util.Scanner;

class Client {
  private String serverAddress;
  private Scanner in;

  Client(String serverAddress) {
    this.serverAddress = serverAddress;
    this.in = new Scanner(System.in);
  }

  void connectAndRun() {
    var redisClient = RedisClient.create("redis://" + serverAddress);
    try (var connection = redisClient.connect()) {
      var syncCommands = connection.sync();
      while (connection.isOpen()) {
        System.out.print("? ");
        runCommand(in.nextLine(), syncCommands);
      }
    } catch (RedisConnectionException e) {
      System.out.println("Could not connect to redis server at " + serverAddress);
    }
    redisClient.shutdown();
  }

  private void runCommand(String command, RedisCommands<String, String> syncCommands) {
    switch (command.trim().toLowerCase()) {
      case "add":
        var book = new HashMap<String, String>();
        System.out.print("Title? ");
        book.put("title", in.nextLine().trim());
        System.out.print("Author? ");
        book.put("author", in.nextLine().trim());
        System.out.print("Year? ");
        book.put("year", in.nextLine().trim());
        book.put("available", "1");
        var id = syncCommands.incr("book:id");
        syncCommands.multi();
        syncCommands.hmset("book:" + id, book);
        syncCommands.zadd("titles", 0, book.get("title") + ":" + id);
        syncCommands.exec();
        break;
      case "get":
        System.out.print("Title? ");
        var title = in.nextLine().trim();
        var matches = syncCommands.zrangebylex("titles", Range.create(title, title + "\\xff"));
        for (String match : matches) {
          System.out.println(match.split(":")[0]);
        }
        break;
      case "borrow":
        System.out.print("Title? ");
        title = in.nextLine().trim();
        matches = syncCommands.zrangebylex("titles", Range.create(title, title + "\\xff"));
        if (matches.size() == 1) {
          var matchId = matches.get(0).split(":")[1];
          if (syncCommands.hget("book:" + matchId, "available").equals("0")) {
            System.out.println(title + " is already borrowed");
          } else {
            syncCommands.hset("book:" + matchId, "available", "0");
          }
        } else {
          System.out.println("More than one book matches " + title);
        }
        break;
      case "return":
        System.out.print("Title? ");
        title = in.nextLine().trim();
        matches = syncCommands.zrangebylex("titles", Range.create(title, title + "\\xff"));
        if (matches.size() == 1) {
          var matchId = matches.get(0).split(":")[1];
          if (syncCommands.hget("book:" + matchId, "available").equals("1")) {
            System.out.println(title + " is not borrowed");
          } else {
            syncCommands.hset("book:" + matchId, "available", "1");
          }
        } else {
          System.out.println("More than one book matches " + title);
        }
        break;
      default:
        System.out.println(command + " is not a command");
    }
  }
}
