package library.model;

public class Hold {
    public String itemId;
    public String itemTitle;
    public boolean ready;

    public Hold() {}

    public Hold(String itemId, String itemTitle) {
        this.itemId = itemId;
        this.itemTitle = itemTitle;
        this.ready = false;
    }
}
