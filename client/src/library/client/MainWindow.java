package library.client;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import library.model.Hold;
import library.model.Item;
import library.model.Loan;
import library.model.Member;
import library.model.Message;
import library.model.Review;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;


class MainWindow {

    private final Client client;
    private final Member member;
    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");

    private final ObservableList<Item> catalogData = FXCollections.observableArrayList();
    private final TableView<Item> catalogTable = new TableView<>(catalogData);
    private final TextField searchField = new TextField();
    private final ComboBox<String> kindFilter = new ComboBox<>();
    private final ComboBox<String> tagFilter = new ComboBox<>();
    private final ComboBox<String> sortBy = new ComboBox<>();
    private final VBox detailsBox = new VBox(8);
    private Item selectedItem;
    private boolean suppressRefresh = false;

    private final ObservableList<Loan> loanData = FXCollections.observableArrayList();
    private final ObservableList<Hold> holdData = FXCollections.observableArrayList();

    MainWindow(Client client, Member member) {
        this.client = client;
        this.member = member;
    }

    Pane buildRoot() {
        String roleSuffix;
        if ("LIBRARIAN".equals(member.role)) {
            roleSuffix = " (librarian)";
        }
        else {
            roleSuffix = "";
        }
        Label welcome = new Label("Welcome, " + member.username + roleSuffix);
        welcome.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: white;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button changePwBtn = new Button("Change Password");
        changePwBtn.setOnAction(e -> showChangePasswordDialog());

        Button logoutBtn = new Button("Log Out");
        logoutBtn.setOnAction(e -> client.logout());

        Button quitBtn = new Button("Quit");
        quitBtn.setOnAction(e -> client.quit());

        HBox header = new HBox(10, welcome, spacer, changePwBtn, logoutBtn, quitBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12));
        header.setStyle("-fx-background-color: #2c3e50;");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab catalogTab = new Tab("Catalog", buildCatalogTab());
        Tab myItemsTab = new Tab("My Items", buildMyItemsTab());
        tabs.getTabs().addAll(catalogTab, myItemsTab);
        if ("LIBRARIAN".equals(member.role)) {
            tabs.getTabs().add(new Tab("Librarian", buildLibrarianTab()));
        }

        tabs.getSelectionModel().selectedItemProperty().addListener((obs, oldT, newT) -> {
            if (newT == catalogTab) {
                refreshCatalog();
            }
            else if (newT == myItemsTab) {
                refreshMyItems();
            }
        });

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(tabs);

        refreshCatalog();
        refreshMyItems();
        return root;
    }

    private void showChangePasswordDialog() {
        javafx.scene.control.Dialog<javafx.util.Pair<String, String>> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText("Enter your current and new password");

        javafx.scene.control.PasswordField oldPw = new javafx.scene.control.PasswordField();
        oldPw.setPromptText("Current password");
        javafx.scene.control.PasswordField newPw = new javafx.scene.control.PasswordField();
        newPw.setPromptText("New password (min 4 chars)");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.add(new Label("Current:"), 0, 0);
        grid.add(oldPw, 1, 0);
        grid.add(new Label("New:"), 0, 1);
        grid.add(newPw, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(
            javafx.scene.control.ButtonType.OK,
            javafx.scene.control.ButtonType.CANCEL
        );

        dialog.setResultConverter(button -> {
            if (button == javafx.scene.control.ButtonType.OK) {
                return new javafx.util.Pair<>(oldPw.getText(), newPw.getText());
            }
            return null;
        });

        java.util.Optional<javafx.util.Pair<String, String>> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }
        javafx.util.Pair<String, String> pair = result.get();
        if (pair.getKey().isEmpty() || pair.getValue().isEmpty()) {
            Client.showAlert("Both fields are required.", true);
            return;
        }

        Message req = new Message("reset_password");
        req.password = pair.getKey();
        req.newPassword = pair.getValue();
        client.sendAsync(req, resp -> {
            boolean isError = !"ok".equals(resp.status);
            Client.showAlert(resp.detail, isError);
        });
    }

    private Node buildCatalogTab() {
        searchField.setPromptText("Search title, creator, or summary");
        searchField.setPrefWidth(280);
        searchField.textProperty().addListener((o, a, b) -> refreshCatalog());

        kindFilter.getItems().addAll("All kinds", "book", "audiobook", "dvd", "game");
        kindFilter.getSelectionModel().selectFirst();
        kindFilter.valueProperty().addListener((o, a, b) -> refreshCatalog());

        tagFilter.getItems().add("All tags");
        tagFilter.getSelectionModel().selectFirst();
        tagFilter.valueProperty().addListener((o, a, b) -> refreshCatalog());

        sortBy.getItems().addAll("title", "year", "rating");
        sortBy.getSelectionModel().selectFirst();
        sortBy.valueProperty().addListener((o, a, b) -> refreshCatalog());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> refreshCatalog());

        HBox toolbar = new HBox(8,
                new Label("Search:"), searchField,
                new Label("Kind:"), kindFilter,
                new Label("Tag:"), tagFilter,
                new Label("Sort:"), sortBy,
                refreshBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10));

        TableColumn<Item, String> c1 = new TableColumn<>("Kind");
        c1.setCellValueFactory(c -> new SimpleStringProperty(cap(c.getValue().kind)));
        c1.setPrefWidth(90);

        TableColumn<Item, String> c2 = new TableColumn<>("Title");
        c2.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().title));
        c2.setPrefWidth(260);

        TableColumn<Item, String> c3 = new TableColumn<>("Creator");
        c3.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().creator));
        c3.setPrefWidth(180);

        TableColumn<Item, String> c4 = new TableColumn<>("Year");
        c4.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().year)));
        c4.setPrefWidth(60);

        TableColumn<Item, String> c5 = new TableColumn<>("Avail");
        c5.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().availableCopies + "/" + c.getValue().totalCopies));
        c5.setPrefWidth(70);

        TableColumn<Item, String> c6 = new TableColumn<>("Rating");
        c6.setCellValueFactory(c -> {
            double r = c.getValue().averageRating();
            String text;
            if (r == 0) {
                text = "-";
            }
            else {
                text = String.format("%.1f", r);
            }
            return new SimpleStringProperty(text);
        });
        c6.setPrefWidth(60);

        catalogTable.getColumns().addAll(c1, c2, c3, c4, c5, c6);
        catalogTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        catalogTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            selectedItem = b;
            showDetails(b);
        });

        detailsBox.setPadding(new Insets(12));
        detailsBox.setPrefWidth(340);
        showDetails(null);

        BorderPane pane = new BorderPane();
        pane.setTop(toolbar);
        pane.setCenter(catalogTable);
        pane.setRight(detailsBox);
        return pane;
    }

    private void refreshCatalog() {
        if (suppressRefresh) {
            return;
        }
        Message req = new Message("search");
        req.query = searchField.getText();

        String kv = kindFilter.getValue();
        if (kv == null || "All kinds".equals(kv)) {
            req.kindFilter = null;
        }
        else {
            req.kindFilter = kv;
        }

        String tv = tagFilter.getValue();
        if (tv == null || "All tags".equals(tv)) {
            req.tag = null;
        }
        else {
            req.tag = tv;
        }

        req.sortBy = sortBy.getValue();
        client.sendAsync(req, resp -> {
            refreshMyItems();
            if ("ok".equals(resp.status) && resp.items != null) {
                catalogData.setAll(resp.items);
                rebuildTagFilter(resp.items);
                if (selectedItem != null) {
                    for (Item it : resp.items) {
                        if (it.id.equals(selectedItem.id)) {
                            showDetails(it);
                            break;
                        }
                    }
                }
            }
        });
    }

    private void rebuildTagFilter(List<Item> items) {
        suppressRefresh = true;
        try {
            TreeSet<String> tags = new TreeSet<>();
            for (Item it : items) {
                if (it.tags != null) {
                    tags.addAll(it.tags);
                }
            }
            String current = tagFilter.getValue();
            tagFilter.getItems().setAll("All tags");
            tagFilter.getItems().addAll(tags);
            if (current != null && tagFilter.getItems().contains(current)) {
                tagFilter.setValue(current);
            }
            else {
                tagFilter.getSelectionModel().selectFirst();
            }
        }
        finally {
            suppressRefresh = false;
        }
    }

    private void showDetails(Item item) {
        detailsBox.getChildren().clear();
        if (item == null) {
            Label hint = new Label("Select an item to see details.");
            hint.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
            detailsBox.getChildren().add(hint);
            return;
        }

        Label title = new Label(item.title);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label by = new Label("by " + nn(item.creator) + " (" + item.year + ")");

        Label desc = new Label(item.describe());
        desc.setStyle("-fx-text-fill: #555;");

        Label summary = new Label(nn(item.summary));
        summary.setWrapText(true);

        String tagsText;
        if (item.tags == null || item.tags.isEmpty()) {
            tagsText = "(none)";
        }
        else {
            tagsText = String.join(", ", item.tags);
        }
        Label tagsL = new Label("Tags: " + tagsText);

        Label avail = new Label("Copies: " + item.availableCopies + "/" + item.totalCopies);

        boolean hasReadyHold = false;
        boolean hasAnyHold = false;
        for (Hold h : holdData) {
            if (h.itemId.equals(item.id)) {
                hasAnyHold = true;
                if (h.ready) {
                    hasReadyHold = true;
                }
                break;
            }
        }
        boolean canBorrow = item.availableCopies > 0 || hasReadyHold;
        boolean canHold = item.availableCopies <= 0 && !hasAnyHold;

        Button borrow = new Button("Borrow");
        borrow.setDisable(!canBorrow);
        borrow.setOnAction(e -> doAction("borrow", item.id, "Borrowed!"));

        Button hold = new Button("Place Hold");
        hold.setDisable(!canHold);
        hold.setOnAction(e -> doAction("hold", item.id, "Hold placed."));

        HBox actions = new HBox(8, borrow, hold);

        Label rHeader = new Label("Leave a review");
        rHeader.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 0 0;");

        Spinner<Integer> rating = new Spinner<>(1, 5, 5);
        rating.setPrefWidth(70);

        TextArea rText = new TextArea();
        rText.setPrefRowCount(2);
        rText.setPromptText("A few words...");

        Button submit = new Button("Submit review");
        submit.setOnAction(e -> {
            Message req = new Message("review");
            req.itemId = item.id;
            req.rating = rating.getValue();
            req.reviewText = rText.getText();
            client.sendAsync(req, resp -> {
                boolean isError = !"ok".equals(resp.status);
                Client.showAlert(resp.detail, isError);
                if ("ok".equals(resp.status)) {
                    rText.clear();
                    refreshCatalog();
                }
            });
        });

        HBox rRow = new HBox(6, new Label("Rating:"), rating, submit);
        rRow.setAlignment(Pos.CENTER_LEFT);

        VBox existing = new VBox(4);
        int reviewCount;
        if (item.reviews == null) {
            reviewCount = 0;
        }
        else {
            reviewCount = item.reviews.size();
        }
        Label eHeader = new Label("Reviews (" + reviewCount + ")");
        eHeader.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 0 0;");
        existing.getChildren().add(eHeader);
        if (item.reviews != null) {
            for (Review r : item.reviews) {
                Label entry = new Label("* " + r.rating + " - " + r.author + " (" + DF.format(new Date(r.timestamp)) + ")\n" + nn(r.text));
                entry.setWrapText(true);
                entry.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 6;");
                existing.getChildren().add(entry);
            }
        }

        detailsBox.getChildren().addAll(title, by, desc, summary, tagsL, avail,
                actions, rHeader, rText, rRow, existing);
    }

    private void doAction(String action, String itemId, String okMsg) {
        Message req = new Message(action);
        req.itemId = itemId;
        client.sendAsync(req, resp -> {
            boolean isError = !"ok".equals(resp.status);
            String message;
            if ("ok".equals(resp.status)) {
                message = okMsg;
            }
            else {
                message = resp.detail;
            }
            Client.showAlert(message, isError);
            if ("ok".equals(resp.status)) {
                refreshCatalog();
                refreshMyItems();
            }
        });
    }

    private Node buildMyItemsTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        Label lH = new Label("Currently borrowed");
        lH.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        TableView<Loan> loans = new TableView<>(loanData);
        TableColumn<Loan, String> l1 = new TableColumn<>("Title");
        l1.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().itemTitle));
        l1.setPrefWidth(300);

        TableColumn<Loan, String> l2 = new TableColumn<>("Borrowed");
        l2.setCellValueFactory(c -> new SimpleStringProperty(DF.format(new Date(c.getValue().borrowedAt))));
        l2.setPrefWidth(120);

        TableColumn<Loan, String> l3 = new TableColumn<>("Due");
        l3.setCellValueFactory(c -> new SimpleStringProperty(DF.format(new Date(c.getValue().dueAt))));
        l3.setPrefWidth(120);

        loans.getColumns().addAll(l1, l2, l3);
        loans.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        loans.setPrefHeight(220);

        Button returnBtn = new Button("Return selected");
        returnBtn.setOnAction(e -> {
            Loan sel = loans.getSelectionModel().getSelectedItem();
            if (sel == null) {
                Client.showAlert("Select a row first.", true);
                return;
            }
            Message req = new Message("return");
            req.itemId = sel.itemId;
            client.sendAsync(req, resp -> {
                boolean isError = !"ok".equals(resp.status);
                String message;
                if ("ok".equals(resp.status)) {
                    message = "Returned.";
                }
                else {
                    message = resp.detail;
                }
                Client.showAlert(message, isError);
                if ("ok".equals(resp.status)) {
                    refreshCatalog();
                    refreshMyItems();
                }
            });
        });

        Label hH = new Label("Holds");
        hH.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 10 0 0 0;");

        TableView<Hold> holds = new TableView<>(holdData);
        TableColumn<Hold, String> h1 = new TableColumn<>("Title");
        h1.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().itemTitle));
        h1.setPrefWidth(300);

        TableColumn<Hold, String> h2 = new TableColumn<>("Status");
        h2.setCellValueFactory(c -> {
            String status;
            if (c.getValue().ready) {
                status = "Ready";
            }
            else {
                status = "Waiting";
            }
            return new SimpleStringProperty(status);
        });
        h2.setPrefWidth(120);

        holds.getColumns().addAll(h1, h2);
        holds.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        holds.setPrefHeight(180);

        Button cancelBtn = new Button("Cancel selected hold");
        cancelBtn.setOnAction(e -> {
            Hold sel = holds.getSelectionModel().getSelectedItem();
            if (sel == null) {
                Client.showAlert("Select a row first.", true);
                return;
            }
            Message req = new Message("cancel_hold");
            req.itemId = sel.itemId;
            client.sendAsync(req, resp -> {
                boolean isError = !"ok".equals(resp.status);
                String message;
                if ("ok".equals(resp.status)) {
                    message = "Hold cancelled.";
                }
                else {
                    message = resp.detail;
                }
                Client.showAlert(message, isError);
                if ("ok".equals(resp.status)) {
                    refreshCatalog();
                    refreshMyItems();
                }
            });
        });

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> refreshMyItems());

        box.getChildren().addAll(lH, loans, returnBtn, hH, holds, cancelBtn, refresh);
        return box;
    }

    private void refreshMyItems() {
        client.sendAsync(new Message("my_items"), resp -> {
            if ("ok".equals(resp.status)) {
                if (resp.loans == null) {
                    loanData.setAll(new ArrayList<>());
                }
                else {
                    loanData.setAll(resp.loans);
                }
                if (resp.holds == null) {
                    holdData.setAll(new ArrayList<>());
                }
                else {
                    holdData.setAll(resp.holds);
                }
            }
        });
    }

    private Node buildLibrarianTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        Label h = new Label("Add a new catalog item");
        h.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        ComboBox<String> kindCombo = new ComboBox<>();
        kindCombo.getItems().addAll("book", "audiobook", "dvd", "game");
        kindCombo.getSelectionModel().selectFirst();

        TextField titleField = new TextField();
        TextField creatorField = new TextField();
        Spinner<Integer> yearSpinner = new Spinner<>(1800, 2100, 2024);
        Spinner<Integer> copiesSpinner = new Spinner<>(1, 99, 2);
        TextField tagsField = new TextField();
        tagsField.setPromptText("comma-separated");
        TextArea summaryArea = new TextArea();
        summaryArea.setPrefRowCount(2);
        TextField pagesDuration = new TextField();
        pagesDuration.setPromptText("pages / minutes / platform");
        TextField extra = new TextField();
        extra.setPromptText("ISBN / narrator / rating / ESRB");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        int r = 0;
        grid.add(new Label("Kind:"), 0, r);
        grid.add(kindCombo, 1, r++);
        grid.add(new Label("Title:"), 0, r);
        grid.add(titleField, 1, r++);
        grid.add(new Label("Creator:"), 0, r);
        grid.add(creatorField, 1, r++);
        grid.add(new Label("Year:"), 0, r);
        grid.add(yearSpinner, 1, r++);
        grid.add(new Label("Copies:"), 0, r);
        grid.add(copiesSpinner, 1, r++);
        grid.add(new Label("Tags:"), 0, r);
        grid.add(tagsField, 1, r++);
        grid.add(new Label("Summary:"), 0, r);
        grid.add(summaryArea, 1, r++);
        grid.add(new Label("Pages/Min/Platform:"), 0, r);
        grid.add(pagesDuration, 1, r++);
        grid.add(new Label("Extra:"), 0, r);
        grid.add(extra, 1, r++);

        Button submit = new Button("Add to catalog");
        submit.setOnAction(e -> {
            if (titleField.getText().isBlank() || creatorField.getText().isBlank()) {
                Client.showAlert("Title and creator are required.", true);
                return;
            }
            Item it = new Item();
            it.kind = kindCombo.getValue();
            it.title = titleField.getText().trim();
            it.creator = creatorField.getText().trim();
            it.year = yearSpinner.getValue();
            it.totalCopies = copiesSpinner.getValue();
            it.summary = summaryArea.getText();
            if (!tagsField.getText().isBlank()) {
                for (String t : tagsField.getText().split(",")) {
                    String x = t.trim();
                    if (!x.isEmpty()) {
                        it.tags.add(x);
                    }
                }
            }
            try {
                switch (it.kind) {
                    case "book":
                        it.pages = Integer.parseInt(pagesDuration.getText().trim());
                        it.isbn = extra.getText();
                        break;
                    case "audiobook":
                        it.durationMinutes = Integer.parseInt(pagesDuration.getText().trim());
                        it.narrator = extra.getText();
                        break;
                    case "dvd":
                        it.durationMinutes = Integer.parseInt(pagesDuration.getText().trim());
                        it.contentRating = extra.getText();
                        break;
                    case "game":
                        it.platform = pagesDuration.getText();
                        it.esrbRating = extra.getText();
                        break;
                }
            }
            catch (NumberFormatException ex) {
                Client.showAlert("Pages/Minutes must be a number for this kind.", true);
                return;
            }
            Message req = new Message("add_item");
            req.newItem = it;
            client.sendAsync(req, resp -> {
                boolean isError = !"ok".equals(resp.status);
                String message;
                if ("ok".equals(resp.status)) {
                    message = "Item added.";
                }
                else {
                    message = resp.detail;
                }
                Client.showAlert(message, isError);
                if ("ok".equals(resp.status)) {
                    titleField.clear();
                    creatorField.clear();
                    tagsField.clear();
                    summaryArea.clear();
                    pagesDuration.clear();
                    extra.clear();
                    refreshCatalog();
                }
            });
        });

        box.getChildren().addAll(h, grid, submit);

        Label addCopiesHeader = new Label("Add copies to an existing item");
        addCopiesHeader.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 16 0 0 0;");

        ComboBox<Item> itemPicker = new ComboBox<>();
        itemPicker.setPromptText("Pick an item");
        itemPicker.setPrefWidth(360);

        itemPicker.setOnShowing(e -> itemPicker.getItems().setAll(catalogData));

        itemPicker.setCellFactory(lv -> new javafx.scene.control.ListCell<Item>() {
            @Override
            protected void updateItem(Item it, boolean empty) {
                super.updateItem(it, empty);
                if (empty || it == null) {
                    setText(null);
                }
                else {
                    setText(it.title + "  (" + cap(it.kind) + ", currently " + it.totalCopies + " total)");
                }
            }
        });

        itemPicker.setButtonCell(new javafx.scene.control.ListCell<Item>() {
            @Override
            protected void updateItem(Item it, boolean empty) {
                super.updateItem(it, empty);
                if (empty || it == null) {
                    setText("Pick an item");
                }
                else {
                    setText(it.title + "  (" + cap(it.kind) + ", currently " + it.totalCopies + " total)");
                }
            }
        });

        Spinner<Integer> extraSpinner = new Spinner<>(1, 50, 1);
        extraSpinner.setPrefWidth(80);

        Button addCopiesBtn = new Button("Add copies");
        addCopiesBtn.setOnAction(e -> {
            Item picked = itemPicker.getValue();
            if (picked == null) {
                Client.showAlert("Pick an item first.", true);
                return;
            }
            Message req = new Message("add_copies");
            req.itemId = picked.id;
            req.extraCount = extraSpinner.getValue();
            client.sendAsync(req, resp -> {
                boolean isError = !"ok".equals(resp.status);
                Client.showAlert(resp.detail, isError);
                if ("ok".equals(resp.status)) {
                    refreshCatalog();
                }
            });
        });

        HBox addCopiesRow = new HBox(8, new Label("Item:"), itemPicker, new Label("Add:"), extraSpinner, addCopiesBtn);
        addCopiesRow.setAlignment(Pos.CENTER_LEFT);

        box.getChildren().addAll(addCopiesHeader, addCopiesRow);

        return box;
    }

    private static String nn(String s) {
        if (s == null) {
            return "";
        }
        return s;
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) {
            return "?";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}