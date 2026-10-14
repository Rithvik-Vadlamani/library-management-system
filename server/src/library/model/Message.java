package library.model;

import java.util.List;

public class Message {
    public String action;
    public String status;
    public String detail;

    public String username;
    public String password;
    public String newPassword;
    public String itemId;
    public String query;
    public String tag;
    public String kindFilter;
    public String sortBy;
    public int rating;
    public String reviewText;
    public Item newItem;
    public int extraCount;

    public List<Item> items;
    public List<Loan> loans;
    public List<Hold> holds;
    public Member member;

    public Message() {}
    public Message(String action) { 
        this.action = action; 
    }
}
