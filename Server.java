import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Server {
	HashMap<String, DataOutputStream> clients;
	HashSet<String> otherServersClients;
	volatile Socket serverSocket;
	volatile boolean sending = false;
	DataOutputStream outToOtherServer;
	BufferedReader inFromServer;
	ServerSocket welcomeSocket;
	
	class ClientHandler implements Runnable {
		Socket connectionSocket;
		String clientName;
		DataOutputStream outToClient;
		BufferedReader inFromClient;
		String[] clientSentence;
		int TTL;

		@Override
		public void run() {
			try {
				inFromClient = new BufferedReader(new InputStreamReader(
						connectionSocket.getInputStream()));
				outToClient = new DataOutputStream(
						connectionSocket.getOutputStream());
				clientName = inFromClient.readLine();
				System.out.println(clientName + " is connected");
				clients.put(clientName, outToClient);

				EventQueue.invokeLater(new Runnable() {

					@Override
					public void run() {
						notifyOtherServer();
					}
				});

				talkWithClient();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		public void clientRemoved() throws IOException {
			clients.remove(clientName);
			connectionSocket.close();
			notifyOtherServer();
		}

		public ClientHandler(Socket s) {
			connectionSocket = s;
		}

		public void notifyClientOfDroppedMessage() throws IOException {
			outToClient.writeBytes("client:ttlError" + "\n");
		}

		public void route(String message, String destination) throws IOException {
			if (clients.containsKey(destination)) {
				if (TTL > 0) {
					DataOutputStream outToDestination;
					outToDestination = clients.get(destination);
					outToDestination.writeBytes("From: " + clientName + "-> "
							+ message + "\n");
				} else
					notifyClientOfDroppedMessage();
			} else if (otherServersClients.contains(destination)) {
				if (TTL > 1) {
					outToOtherServer.writeBytes(clientName + ':' + destination
							+ ':' + message + "\n");
					System.out.println("Forwarding");
				} else
					notifyClientOfDroppedMessage();
			} else
				outToClient.writeBytes("client:Name not found" + "\n");
		}

		public void talkWithClient() throws IOException {
			while (true) {
				if (!connectionSocket.isConnected()) {
					clientRemoved();
					break;
				}

				clientSentence = inFromClient.readLine().split(":");
				if (clientSentence == null
						|| (clientSentence[0].equals("Server") && clientSentence[1]
								.equals("Quit"))) {
					clientRemoved();
					break;
				}
				if (clientSentence[0].equals("Server")
						&& clientSentence[1].equals("getMembers")) {
					outToClient.writeBytes(MemberListResponse());
					continue;
				}
				TTL = Integer.parseInt(clientSentence[2]);
				route(clientSentence[1], clientSentence[0]);

			}
		}
	}

	@SuppressWarnings("resource")
	public Server() throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		System.out.print("This server's socket:");
		welcomeSocket = new ServerSocket(Integer.parseInt(br.readLine()));
		System.out.print("The other server's socket:");
		clients = new HashMap<String, DataOutputStream>();

		EventQueue.invokeLater(new Runnable() {

			@Override
			public void run() {
				try {
					otherServerHandler(br.readLine());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});

	}

	public void joinResponce(String name) {

	}

	public String MemberListResponse() {
		Set<String> serverHere = clients.keySet();
		String s = (serverHere.size() + otherServersClients.size()) + "\n";
		for (String i : serverHere)
			s += i + "\n";
		for (String i : otherServersClients)
			s += i + "\n";
		return s;
	}

	public void notifyOtherServer() {
		Thread notifyOtherServer = new Thread(new Runnable() {

			@Override
			public void run() {
				while (sending)
					;
				sending = true;
				try {
					Object[] clientArray = clients.keySet().toArray();
					outToOtherServer.writeBytes(clientArray.length + "\n");
					for (int i = 0; i < clientArray.length; i++)
						outToOtherServer.writeBytes((String) clientArray[i]
								+ "\n");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					sending = false;
				}
			}
		});
		notifyOtherServer.start();

	}

	public void otherServerHandler(String otherServerSocketNumber) {
		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					serverSocket = new Socket("127.0.1.1", Integer
							.parseInt(otherServerSocketNumber));
					outToOtherServer = new DataOutputStream(serverSocket
							.getOutputStream());
					inFromServer = new BufferedReader(new InputStreamReader(
							welcomeSocket.accept().getInputStream()));
					System.out.println("Connected to:"
							+ otherServerSocketNumber);

					EventQueue.invokeLater(new Runnable() {

						@Override
						public void run() {
							recieveClients();
						}
					});

					while (true) {
						String s = inFromServer.readLine();
						System.out.println(s);
						try {
							int number = Integer.parseInt(s);
							otherServersClients = new HashSet<String>();
							for (int i = 0; i < number; i++) {
								s = inFromServer.readLine();
								otherServersClients.add(s);
							}
							continue;
						} catch (NumberFormatException e) {
							String[] header = s.split(":");
							DataOutputStream toSelectedClient = clients
									.get(header[1]);
							toSelectedClient.writeBytes("From: " + header[0]
									+ "-> " + header[2] + "\n");
						}
					}

				} catch (UnknownHostException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
		t.start();
	}

	public void recieveClients() {
		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors
						.newFixedThreadPool(20);
				while (true) {
					Socket connectionSocket;
					try {
						connectionSocket = welcomeSocket.accept();
						pool.execute(new ClientHandler(connectionSocket));
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		});
		t.start();
	}

	public static void main(String[] args) throws IOException {
		new Server();
	}

}