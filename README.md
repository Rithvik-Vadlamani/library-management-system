# Library management system

A JavaFX client and multithreaded Java server for browsing a catalog, borrowing items, and managing holds. The client exchanges JSON messages with the server over TCP using Gson.

I wrote the client/server application to handle multiple users without blocking the interface. Borrowing and return operations are synchronized so two clients cannot check out the same available copy. Returned items can be reserved for the next member in the hold queue.

## Run it

Requires JDK 17 or newer and Maven 3.9+. Build both modules and run the existing JUnit tests:

```sh
mvn test
```

In one terminal, start the server from its directory so it can find the sample catalog:

```sh
cd server
mvn compile exec:java
```

In another terminal, start the client:

```sh
cd client
mvn javafx:run
```

Connect to `localhost:4242`, then register a new account. The published account list starts empty; stored personal accounts and password hashes are excluded.

## Where to look

- [Client.java](client/src/library/client/Client.java): connection, login, and background socket communication.
- [MainWindow.java](client/src/library/client/MainWindow.java): catalog and account interface.
- [Server.java](server/src/library/server/Server.java) and [ClientHandler.java](server/src/library/server/ClientHandler.java): connection acceptance and per-client request handling.
- [Library.java](server/src/library/server/Library.java): synchronized library operations and JSON persistence.
- [LibraryTest.java](server/src/library/server/LibraryTest.java): login, registration, borrowing, holds, and concurrency tests using temporary directories.

This is a local application demo. Its TCP connection is not encrypted, and its account handling is not intended for a public production service. Use fictional accounts when trying it.
