package library.client;

import com.google.gson.Gson;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import library.model.Member;
import library.model.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Client extends Application {

    private Stage stage;
    private Scene scene;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private final Gson gson = new Gson();
    private Member currentMember;

    @Override
    public void start(Stage primary) {
        this.stage = primary;
        primary.setTitle("Online Library");
        primary.setOnCloseRequest(e -> shutdown());
        showConnectScreen();
        primary.show();
    }

    private void showConnectScreen() {
        Label title = new Label("Online Library");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        TextField hostField = new TextField("127.0.0.1");
        TextField portField = new TextField("4242");
        hostField.setMaxWidth(200);
        portField.setMaxWidth(80);
        HBox addr = new HBox(8, hostField, portField);
        addr.setAlignment(Pos.CENTER);

        Label status = new Label(" ");
        status.setStyle("-fx-text-fill: #c0392b;");
        Button connectBtn = new Button("Connect");
        connectBtn.setDefaultButton(true);
        connectBtn.setOnAction(e -> {
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
            }
            catch (NumberFormatException ex) {
                status.setText("Bad port");
                return;
            }
            try {
                socket = new Socket(hostField.getText().trim(), port);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), false);
                showLoginScreen();
            }
            catch (IOException ex) {
                status.setText("Connect failed: " + ex.getMessage());
            }
        });

        VBox box = new VBox(12, title, new Label("Connect to server"), addr, connectBtn, status);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));

        setRoot(box, 420, 300);
    }

    private void showLoginScreen() {
        Label title = new Label("Online Library");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        TextField user = new TextField();
        user.setPromptText("Username");
        user.setMaxWidth(240);
        PasswordField pw = new PasswordField();
        pw.setPromptText("Password");
        pw.setMaxWidth(240);
        Label status = new Label(" ");
        status.setStyle("-fx-text-fill: #c0392b;");

        Button loginBtn = new Button("Log In");
        Button regBtn = new Button("Register");
        loginBtn.setDefaultButton(true);

        Runnable doLogin = () -> tryLogin(user.getText(), pw.getText(), status, "login");
        Runnable doReg   = () -> tryLogin(user.getText(), pw.getText(), status, "register");

        loginBtn.setOnAction(e -> doLogin.run());
        regBtn.setOnAction(e -> doReg.run());
        pw.setOnAction(e -> doLogin.run());

        HBox btns = new HBox(8, loginBtn, regBtn);
        btns.setAlignment(Pos.CENTER);

        VBox box = new VBox(10, title, user, pw, btns, status);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));

        setRoot(box, 420, 320);
    }

    private void tryLogin(String username, String password, Label status, String action) {
        if (username.isBlank() || password.isEmpty()) {
            status.setText("Username and password required.");
            return;
        }
        status.setText("Connecting...");
        Message req = new Message(action);
        req.username = username.trim();
        req.password = password;
        sendAsync(req, resp -> {
            if ("ok".equals(resp.status)) {
                currentMember = resp.member;
                showMainWindow();
            }
            else {
                if (resp.detail == null) {
                    status.setText("Login failed.");
                }
                else {
                    status.setText(resp.detail);
                }
            }
        });
    }

    private void showMainWindow() {
        MainWindow mw = new MainWindow(this, currentMember);
        setRoot(mw.buildRoot(), 1150, 700);
    }

    void logout() {
        Message req = new Message("logout");
        sendAsync(req, r -> {});
        currentMember = null;
        showLoginScreen();
    }

    void quit() {
        shutdown();
        Platform.exit();
        System.exit(0);
    }

    private void shutdown() {
        try {
            if (socket != null) {
                socket.close();
            }
        }
        catch (IOException ignored) {
        }
    }

    void sendAsync(Message req, java.util.function.Consumer<Message> onResponse) {
        Thread t = new Thread(() -> {
            Message resp;
            try {
                resp = sendSync(req);
            }
            catch (IOException e) {
                resp = new Message("response");
                resp.status = "error";
                resp.detail = "Network error: " + e.getMessage();
            }
            final Message r = resp;
            Platform.runLater(() -> onResponse.accept(r));
        });
        t.setDaemon(true);
        t.start();
    }

    private synchronized Message sendSync(Message req) throws IOException {
        String json = gson.toJson(req);
        out.println(json);
        out.flush();
        String line = in.readLine();
        if (line == null) {
            throw new IOException("Connection closed");
        }
        return gson.fromJson(line, Message.class);
    }

    Member getCurrentMember() {
        return currentMember;
    }

    private void setRoot(Pane p, double w, double h) {
        if (scene == null) {
            scene = new Scene(p, w, h);
            stage.setScene(scene);
        }
        else {
            scene.setRoot(p);
            stage.setWidth(w);
            stage.setHeight(h);
        }
    }

    static void showAlert(String message, boolean error) {
        Alert.AlertType type;
        if (error) {
            type = Alert.AlertType.ERROR;
        }
        else {
            type = Alert.AlertType.INFORMATION;
        }
        Alert a = new Alert(type, message);
        a.setHeaderText(null);
        a.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}