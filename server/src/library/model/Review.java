package library.model;

public class Review {
    public String author;
    public int rating;
    public String text;
    public long timestamp;

    public Review() {}

    public Review(String author, int rating, String text) {
        this.author = author;
        this.rating = rating;
        if (text == null) {
            this.text = "";
        } 
        else {
            this.text = text;
        }
        this.timestamp = System.currentTimeMillis();
    }
}
