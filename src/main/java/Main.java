public class Main {

  public static void main(String[] args) {
    String serverAddress = "127.0.0.1:6379";
    Client client = new Client(serverAddress);
    client.connectAndRun();
  }
}
