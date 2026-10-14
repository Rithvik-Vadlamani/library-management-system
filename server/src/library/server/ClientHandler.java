package library.server;

import com.google.gson.Gson;

import library.model.Member;
import library.model.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

class ClientHandler implements Runnable {

    private final Library library;
    private final Socket socket;
    private final Gson gson = new Gson();
    private Member loggedIn;

    ClientHandler(Library library, Socket socket) {
        this.library = library;
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), false)) {

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[<-] " + line);
                Message req;
                try {
                    req = gson.fromJson(line, Message.class);
                }
                catch (Exception e) {
                    sendError(out, "Bad JSON");
                    continue;
                }
                Message resp = dispatch(req);
                String json = gson.toJson(resp);
                System.out.println("[->] " + json);
                out.println(json);
                out.flush();
            }
        }
        catch (IOException e) {
            // Normal disconnect.
        }
        finally {
            try {
                socket.close();
            }
            catch (IOException ignored) {
            }
            System.out.println("[server] client disconnected");
        }
    }

    private Message dispatch(Message req) {
        if (req == null || req.action == null) {
            return error("Empty request.");
        }
        try {
            switch (req.action) {
                case "login":
                    return doLogin(req);
                case "register":
                    return doRegister(req);
                case "logout":
                    loggedIn = null;
                    return ok("Logged out.");
                case "search":
                    return doSearch(req);
                case "borrow":
                    return doBorrow(req);
                case "return":
                    return doReturn(req);
                case "hold":
                    return doHold(req);
                case "cancel_hold":
                    return doCancelHold(req);
                case "review":
                    return doReview(req);
                case "my_items":
                    return doMyItems();
                case "add_item":
                    return doAddItem(req);
                case "add_copies":
                    return doAddCopies(req);
                case "reset_password":
                    return doResetPassword(req);
                default:
                    return error("Unknown action: " + req.action);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            return error("Server error: " + e.getMessage());
        }
    }

    private Message doLogin(Message req) {
        Member m = library.login(req.username, req.password);
        if (m == null) {
            return error("Invalid username or password.");
        }
        loggedIn = m;
        Message r = ok("Welcome, " + m.username);
        r.member = safeCopy(m);
        return r;
    }

    private Message doRegister(Message req) {
        Member m = library.register(req.username, req.password);
        if (m == null) {
            return error("Username taken or invalid.");
        }
        loggedIn = m;
        Message r = ok("Account created.");
        r.member = safeCopy(m);
        return r;
    }

    private Message doSearch(Message req) {
        if (loggedIn == null) {
            return error("Please log in.");
        }
        Message r = ok(null);
        r.items = library.searchCatalog(req.query, req.kindFilter, req.tag, req.sortBy);
        return r;
    }

    private Message doBorrow(Message req) {
        if (loggedIn == null) {
            return error("Please log in.");
        }
        String err = library.borrow(loggedIn, req.itemId);
        if (err != null) {
            return error(err);
        }
        Message r = ok("Borrowed.");
        r.loans = new java.util.ArrayList<>(loggedIn.loans);
        return r;
    }

    private Message doReturn(Message req) {
        if (loggedIn == null) {
            return error("Please log in.");
        }
        String err = library.returnItem(loggedIn, req.itemId);
        if (err != null) {
            return error(err);
        }
        Message r = ok("Returned.");
        r.loans = new java.util.ArrayList<>(loggedIn.loans);
        return r;
    }

    private Message doHold(Message req) {
        if (loggedIn == null) {
            return error("Please log in.");
        }
        String err = library.placeHold(loggedIn, req.itemId);
        if (err != null) {
            return error(err);
        }
        Message r = ok("Hold placed.");
        r.holds = new java.util.ArrayList<>(loggedIn.holds);
        return r;
    }

    private Message doCancelHold(Message req) {
        if (loggedIn == null) {
            return error("Please log in.");
        }
        String err = library.cancelHold(loggedIn, req.itemId);
        if (err != null) {
            return error(err);
        }
        Message r = ok("Hold cancelled.");
        r.holds = new java.util.ArrayList<>(loggedIn.holds);
        return r;
    }

    private Message doReview(Message req) {
        if (loggedIn == null) {
            return error("Please log in.");
        }
        String err = library.addReview(loggedIn, req.itemId, req.rating, req.reviewText);
        if (err != null) {
            return error(err);
        }
        return ok("Review submitted.");
    }

    private Message doMyItems() {
        if (loggedIn == null) {
            return error("Please log in.");
        }
        Message r = ok(null);
        r.loans = new java.util.ArrayList<>(loggedIn.loans);
        r.holds = new java.util.ArrayList<>(loggedIn.holds);
        return r;
    }

    private Message doAddItem(Message req) {
        if (loggedIn == null) {
            return error("Please log in.");
        }
        if (!"LIBRARIAN".equals(loggedIn.role)) {
            return error("Librarians only.");
        }
        String err = library.addItem(req.newItem);
        if (err != null) {
            return error(err);
        }
        return ok("Item added.");
    }
    private Message doAddCopies(Message req) {
        if (loggedIn == null) {
            return error("Please log in.");
        }
        if (!"LIBRARIAN".equals(loggedIn.role)) {
            return error("Librarians only.");
        }
        String err = library.addCopies(req.itemId, req.extraCount);
        if (err != null) {
            return error(err);
        }
        String unit;
        if (req.extraCount == 1) {
            unit = "copy";
        }
        else {
            unit = "copies";
        }
        return ok("Added " + req.extraCount + " " + unit + ".");
    }

    private Message doResetPassword(Message req) {
        if (loggedIn == null) {
            return error("Please log in.");
        }
        String err = library.resetPassword(loggedIn, req.password, req.newPassword);
        if (err != null) {
            return error(err);
        }
        return ok("Password updated.");
    }

    private static Message ok(String detail) {
        Message m = new Message("response");
        m.status = "ok";
        m.detail = detail;
        return m;
    }

    private static Message error(String detail) {
        Message m = new Message("response");
        m.status = "error";
        m.detail = detail;
        return m;
    }

    private void sendError(PrintWriter out, String detail) {
        String json = gson.toJson(error(detail));
        out.println(json);
        out.flush();
    }

    private static Member safeCopy(Member src) {
        Member copy = new Member(src.username, null, null, src.role);
        copy.loans = new java.util.ArrayList<>(src.loans);
        copy.holds = new java.util.ArrayList<>(src.holds);
        return copy;
    }
}