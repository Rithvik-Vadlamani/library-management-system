package library.model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Item {
    public String id;
    public String kind;          // book, audiobook, dvd, game
    public String title;
    public String creator;       // author, narrator, director, studio
    public String summary;
    public int year;
    public int totalCopies;
    public int availableCopies;
    public List<String> tags = new ArrayList<>();
    public List<Review> reviews = new ArrayList<>();
    public LinkedList<String> holdQueue = new LinkedList<>();

    public int pages;            // book
    public String isbn;          // book
    public int durationMinutes;  // audiobook, DVD
    public String narrator;      // audiobook
    public String contentRating; // dvd
    public String platform;      // game
    public String esrbRating;    // game

    public Item() {}

    public double averageRating() {
        if (reviews == null || reviews.isEmpty()){
            return 0.0;
        }
        double sum = 0;
        for (Review r : reviews){
            sum += r.rating;
        }
        return sum / reviews.size();
    }

    public String describe() {
        if (kind == null) {
            return title;
        } 
        else if (kind.equals("book")) {
            return "Book - " + pages + " pages";
        } 
        else if (kind.equals("audiobook")) {
            return "Audiobook - " + durationMinutes + " min, read by " + narrator;
        } 
        else if (kind.equals("dvd")) {
            return "DVD - " + durationMinutes + " min, rated " + contentRating;
        } 
        else if (kind.equals("game")) {
            return "Game - " + platform + ", " + esrbRating;
        } 
        else {
            return title;
        }
    }
}
