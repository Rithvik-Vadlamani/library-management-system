package library.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import library.model.Hold;
import library.model.Item;
import library.model.Loan;
import library.model.Member;
import library.model.Review;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class Library {

    private final Map<String, Item> items = new HashMap<>();
    private final Map<String, Member> members = new HashMap<>();
    private final String catalogPath;
    private final String membersPath;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final SecureRandom rng = new SecureRandom();

    public Library(String dataDir) {
        this.catalogPath = dataDir + "/catalog.json";
        this.membersPath = dataDir + "/members.json";
        loadCatalog();
        loadMembers();

        Timer timer = new Timer("overdue-sweeper", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sweepOverdue();
            }
        }, 60_000, 60_000);
    }

    private void loadCatalog() {
        try (FileReader r = new FileReader(catalogPath, StandardCharsets.UTF_8)) {
            List<Item> list = gson.fromJson(r, new TypeToken<List<Item>>(){}.getType());
            if (list != null) {
                for (Item it : list) {
                    items.put(it.id, it);
                }
            }
        }
        catch (IOException e) {
            System.out.println("[library] no catalog file, starting empty");
        }
    }

    private void loadMembers() {
        try (FileReader r = new FileReader(membersPath, StandardCharsets.UTF_8)) {
            List<Member> list = gson.fromJson(r, new TypeToken<List<Member>>(){}.getType());
            if (list != null) {
                for (Member m : list) {
                    members.put(m.username.toLowerCase(), m);
                }
            }
        }
        catch (IOException e) {
            System.out.println("[library] no members file, starting empty");
        }
    }

    private synchronized void saveCatalog() {
        try (FileWriter w = new FileWriter(catalogPath, StandardCharsets.UTF_8)) {
            gson.toJson(new ArrayList<>(items.values()), w);
        }
        catch (IOException e) {
            System.err.println("[library] save catalog failed: " + e.getMessage());
        }
    }

    private synchronized void saveMembers() {
        try (FileWriter w = new FileWriter(membersPath, StandardCharsets.UTF_8)) {
            gson.toJson(new ArrayList<>(members.values()), w);
        }
        catch (IOException e) {
            System.err.println("[library] save members failed: " + e.getMessage());
        }
    }

    public int itemCount() {
        synchronized (this) {
            return items.size();
        }
    }

    public int memberCount() {
        synchronized (this) {
            return members.size();
        }
    }

    private String hashPassword(String password, String saltHex) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(hexToBytes(saltHex));
            md.update(password.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(md.digest());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String newSalt() {
        byte[] s = new byte[16];
        rng.nextBytes(s);
        return bytesToHex(s);
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String h) {
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public synchronized Member login(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        Member m = members.get(username.toLowerCase());
        if (m == null) {
            return null;
        }
        if (!hashPassword(password, m.salt).equals(m.passwordHash)) {
            return null;
        }
        return m;
    }

    public synchronized Member register(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            return null;
        }
        if (members.containsKey(username.toLowerCase())) {
            return null;
        }
        String salt = newSalt();
        Member m = new Member(username, hashPassword(password, salt), salt, "MEMBER");
        members.put(username.toLowerCase(), m);
        saveMembers();
        return m;
    }

    public synchronized String resetPassword(Member m, String oldPassword, String newPassword) {
        if (m == null) {
            return "Not logged in.";
        }
        if (oldPassword == null || newPassword == null) {
            return "Old and new passwords are required.";
        }
        if (newPassword.length() < 4) {
            return "New password must be at least 4 characters.";
        }
        if (!hashPassword(oldPassword, m.salt).equals(m.passwordHash)) {
            return "Current password is incorrect.";
        }
        String newSalt = newSalt();
        m.salt = newSalt;
        m.passwordHash = hashPassword(newPassword, newSalt);
        saveMembers();
        return null;
    }

    public synchronized List<Item> searchCatalog(String query, String kindFilter, String tag, String sortBy) {
        List<Item> out = new ArrayList<>();
        String q;
        if (query == null) {
            q = "";
        }
        else {
            q = query.toLowerCase().trim();
        }
        for (Item it : items.values()) {
            if (kindFilter != null && !kindFilter.isEmpty() && !kindFilter.equalsIgnoreCase(it.kind)) {
                continue;
            }
            if (tag != null && !tag.isEmpty() && (it.tags == null || !it.tags.contains(tag))) {
                continue;
            }
            if (!q.isEmpty()) {
                String titlePart;
                if (it.title == null) {
                    titlePart = "";
                }
                else {
                    titlePart = it.title;
                }
                String creatorPart;
                if (it.creator == null) {
                    creatorPart = "";
                }
                else {
                    creatorPart = it.creator;
                }
                String summaryPart;
                if (it.summary == null) {
                    summaryPart = "";
                }
                else {
                    summaryPart = it.summary;
                }
                String hay = (titlePart + " " + creatorPart + " " + summaryPart).toLowerCase();
                if (!hay.contains(q)) {
                    continue;
                }
            }
            out.add(it);
        }
        Comparator<Item> cmp;
        if ("year".equalsIgnoreCase(sortBy)) {
            cmp = Comparator.comparingInt((Item i) -> i.year).reversed();
        }
        else if ("rating".equalsIgnoreCase(sortBy)) {
            cmp = Comparator.comparingDouble(Item::averageRating).reversed();
        }
        else {
            cmp = Comparator.comparing(i -> {
                if (i.title == null) {
                    return "";
                }
                return i.title;
            }, String.CASE_INSENSITIVE_ORDER);
        }
        out.sort(cmp);
        return out;
    }

    public synchronized String borrow(Member m, String itemId) {
        Item it = items.get(itemId);
        if (it == null) {
            return "Item not found.";
        }
        for (Loan l : m.loans) {
            if (l.itemId.equals(itemId)) {
                return "You already have this item.";
            }
        }
        boolean reservedForMe = !it.holdQueue.isEmpty() && m.username.equalsIgnoreCase(it.holdQueue.peekFirst());
        if (it.availableCopies <= 0 && !reservedForMe) {
            return "No copies available. You may place a hold.";
        }
        if (!reservedForMe) {
            it.availableCopies--;
        }
        m.loans.add(new Loan(it.id, it.title));
        if (reservedForMe) {
            it.holdQueue.removeFirst();
            m.holds.removeIf(h -> h.itemId.equals(itemId));
        }
        saveCatalog();
        saveMembers();
        return null;
    }

    public synchronized String returnItem(Member m, String itemId) {
        Item it = items.get(itemId);
        if (it == null) {
            return "Item not found.";
        }
        Iterator<Loan> iter = m.loans.iterator();
        boolean found = false;
        while (iter.hasNext()) {
            if (iter.next().itemId.equals(itemId)) {
                iter.remove();
                found = true;
                break;
            }
        }
        if (!found) {
            return "You don't have this item.";
        }
        if (!it.holdQueue.isEmpty()) {
            String waiter = it.holdQueue.peekFirst();
            Member wm = members.get(waiter.toLowerCase());
            if (wm != null) {
                for (Hold h : wm.holds) {
                    if (h.itemId.equals(itemId)) {
                        h.ready = true;
                        break;
                    }
                }
            }
        }
        else {
            it.availableCopies++;
        }
        saveCatalog();
        saveMembers();
        return null;
    }

    public synchronized String placeHold(Member m, String itemId) {
        Item it = items.get(itemId);
        if (it == null) {
            return "Item not found.";
        }
        if (it.availableCopies > 0) {
            return "Item is available - just borrow it.";
        }
        if (it.holdQueue.contains(m.username)) {
            return "You already have a hold on this.";
        }
        it.holdQueue.addLast(m.username);
        m.holds.add(new Hold(it.id, it.title));
        saveCatalog();
        saveMembers();
        return null;
    }

    public synchronized String cancelHold(Member m, String itemId) {
        Item it = items.get(itemId);
        if (it == null) {
            return "Item not found.";
        }
        boolean removed = it.holdQueue.removeIf(u -> u.equalsIgnoreCase(m.username));
        for (Hold h : m.holds) {
            if (h.itemId.equals(itemId) && h.ready) {
                if (!it.holdQueue.isEmpty()) {
                    Member next = members.get(it.holdQueue.peekFirst().toLowerCase());
                    if (next != null) {
                        for (Hold nh : next.holds) {
                            if (nh.itemId.equals(itemId)) {
                                nh.ready = true;
                                break;
                            }
                        }
                    }
                }
                else {
                    it.availableCopies++;
                }
                break;
            }
        }
        m.holds.removeIf(h -> h.itemId.equals(itemId));
        if (!removed) {
            return "You don't have a hold on that.";
        }
        saveCatalog();
        saveMembers();
        return null;
    }

    public synchronized String addReview(Member m, String itemId, int rating, String text) {
        Item it = items.get(itemId);
        if (it == null) {
            return "Item not found.";
        }
        if (rating < 1 || rating > 5) {
            return "Rating must be 1-5.";
        }
        it.reviews.removeIf(r -> m.username.equalsIgnoreCase(r.author));
        it.reviews.add(new Review(m.username, rating, text));
        saveCatalog();
        return null;
    }

    public synchronized String addItem(Item it) {
        if (it == null || it.title == null || it.title.isBlank()) {
            return "Title required.";
        }
        if (it.id == null || it.id.isBlank()) {
            it.id = it.kind.charAt(0) + "-" + System.currentTimeMillis();
        }
        if (items.containsKey(it.id)) {
            return "Item id already exists.";
        }
        if (it.totalCopies < 1) {
            it.totalCopies = 1;
        }
        it.availableCopies = it.totalCopies;
        if (it.tags == null) {
            it.tags = new ArrayList<>();
        }
        if (it.reviews == null) {
            it.reviews = new ArrayList<>();
        }
        if (it.holdQueue == null) {
            it.holdQueue = new java.util.LinkedList<>();
        }
        items.put(it.id, it);
        saveCatalog();
        return null;
    }
    
    public synchronized String addCopies(String itemId, int extra) {
        Item it = items.get(itemId);
        if (it == null) {
            return "Item not found.";
        }
        if (extra < 1) {
            return "Must add at least 1 copy.";
        }
        it.totalCopies += extra;
        it.availableCopies += extra;
        saveCatalog();
        return null;
    }

    private synchronized void sweepOverdue() {
        boolean changed = false;
        for (Member m : members.values()) {
            List<Loan> copy = new ArrayList<>(m.loans);
            for (Loan l : copy) {
                if (l.isOverdue()) {
                    System.out.println("[sweeper] auto-returning " + l.itemId + " from " + m.username);
                    returnItem(m, l.itemId);
                    changed = true;
                }
            }
        }
        if (changed) {
            System.out.println("[sweeper] swept overdue loans");
        }
    }
}