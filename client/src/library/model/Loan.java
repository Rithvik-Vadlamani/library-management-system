package library.model;
import java.util.concurrent.TimeUnit;

public class Loan {
    public static final long LOAN_PERIOD_MS = TimeUnit.DAYS.toMillis(14);

    public String itemId;
    public String itemTitle;
    public long borrowedAt;
    public long dueAt;

    public Loan() {}

    public Loan(String itemId, String itemTitle) {
        this.itemId = itemId;
        this.itemTitle = itemTitle;
        this.borrowedAt = System.currentTimeMillis();
        this.dueAt = this.borrowedAt + LOAN_PERIOD_MS;
    }

    public boolean isOverdue() {
        return System.currentTimeMillis() > dueAt;
    }
}
