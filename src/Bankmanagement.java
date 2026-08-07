import java.sql.*;
import java.util.Scanner;

public class Bankmanagement{

    // Database Configuration
    private static final String URL = "jdbc:mysql://localhost:3306/Banking_Management";
    private static final String USER = "root";
    private static final String PASS = "Saigeetha12@34";

    public static void main(String[] args) {
        // Step 1: Initialize Database and Tables
        setupDatabaseAndTables();

        // Step 2: Main Menu Loop
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n==========================================");
            System.out.println("       BANKING MANAGEMENT SYSTEM          ");
            System.out.println("==========================================");
            System.out.println("1. Create New Account");
            System.out.println("2. Deposit Money");
            System.out.println("3. Withdraw Money");
            System.out.println("4. Transfer Funds (Safe ACID Transaction)");
            System.out.println("5. View Transaction History");
            System.out.println("6. Exit");
            System.out.print("Select an option (1-6): ");

            int choice = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            switch (choice) {
                case 1:
                    System.out.print("Enter Customer Name: ");
                    String name = scanner.nextLine();
                    System.out.print("Enter Email: ");
                    String email = scanner.nextLine();
                    System.out.print("Enter Phone: ");
                    String phone = scanner.nextLine();
                    System.out.print("Enter Initial Deposit: $");
                    double initialDeposit = scanner.nextDouble();
                    createAccount(name, email, phone, initialDeposit);
                    break;
                case 2:
                    System.out.print("Enter Account Number: ");
                    int depAcc = scanner.nextInt();
                    System.out.print("Enter Deposit Amount: $");
                    double depAmt = scanner.nextDouble();
                    performDepositOrWithdrawal(depAcc, depAmt, "DEPOSIT");
                    break;

                case 3:
                    System.out.print("Enter Account Number: ");
                    int withAcc = scanner.nextInt();
                    System.out.print("Enter Withdrawal Amount: $");
                    double withAmt = scanner.nextDouble();
                    performDepositOrWithdrawal(withAcc, withAmt, "WITHDRAWAL");
                    break;

                case 4:
                    System.out.print("Enter Sender Account Number: ");
                    int sender = scanner.nextInt();
                    System.out.print("Enter Receiver Account Number: ");
                    int receiver = scanner.nextInt();
                    System.out.print("Enter Transfer Amount: $");
                    double transferAmt = scanner.nextDouble();
                    transferFunds(sender, receiver, transferAmt);
                    break;

                case 5:
                    System.out.print("Enter Account Number: ");
                    int historyAcc = scanner.nextInt();
                    printTransactionHistory(historyAcc);
                    break;

                case 6:
                    System.out.println("Thank you for using Banking Management System. Goodbye!");
                    scanner.close();
                    System.exit(0);

                default:
                    System.out.println("Invalid option! Please try again.");
            }
        }
    }
    // 1. DATABASE & TABLES SETUP (JDBC)
    private static void setupDatabaseAndTables() {

        String createCustomerTable = """
CREATE TABLE IF NOT EXISTS customer (
    customer_id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    email VARCHAR(50) NOT NULL,
    phone VARCHAR(15) NOT NULL
)
""";

        String createAccountTable = """
CREATE TABLE IF NOT EXISTS accounts (
    account_number INT PRIMARY KEY AUTO_INCREMENT,
    customer_id INT NOT NULL,
    balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    account_type VARCHAR(20) DEFAULT 'SAVINGS',
    CONSTRAINT chk_balance CHECK (balance >= 0),
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
)
""";

        String createTransactionTable = """
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id INT PRIMARY KEY AUTO_INCREMENT,
    account_number INT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_number) REFERENCES accounts(account_number)
)
""";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createCustomerTable);
            stmt.executeUpdate(createAccountTable);
            stmt.executeUpdate(createTransactionTable);


            System.out.println("Database and tables initialized successfully.");

        } catch (SQLException e) {
            System.err.println("Initialization Error: " + e.getMessage());
        }
    }

    private static Connection getDBConnection() throws SQLException {
        return DriverManager.getConnection(URL , USER, PASS);
    }
    // 2. ACCOUNT CREATION
    public static void createAccount(String name, String email, String phone, double initialDeposit) {
        String insertCustomer = "INSERT INTO customer (name, email, phone) VALUES (?, ?, ?)";
        String insertAccount = "INSERT INTO accounts (customer_id, balance) VALUES (?, ?)";

        try (Connection conn = getDBConnection()) {
            conn.setAutoCommit(false); // Begin Transaction

            int customerId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(insertCustomer, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, name);
                stmt.setString(2, email);
                stmt.setString(3, phone);
                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    customerId = rs.getInt(1);
                }
            }

            int accountNumber = -1;
            try (PreparedStatement stmt = conn.prepareStatement(insertAccount, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, customerId);
                stmt.setDouble(2, initialDeposit);
                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    accountNumber = rs.getInt(1);
                }
            }

            conn.commit(); // Commit Transaction
            System.out.println("Account Created Successfully!");
            System.out.println("Assigned Account Number: " + accountNumber);

        } catch (SQLException e) {
            System.err.println("Account Creation Failed: " + e.getMessage());
        }
    }
    // 3. DEPOSIT & WITHDRAWAL MODULES
    public static void performDepositOrWithdrawal(int accNum, double amount, String type) {
        if (amount <= 0) {
            System.out.println("Amount must be greater than 0.");
            return;
        }

        String checkBalanceSql = "SELECT balance FROM accounts WHERE account_number = ?";
        String updateSql = type.equalsIgnoreCase("DEPOSIT")
                ? "UPDATE accounts SET balance = balance + ? WHERE account_number = ?"
                : "UPDATE accounts SET balance = balance - ? WHERE account_number = ?";
        String logSql = "INSERT INTO transactions (account_number, transaction_type, amount) VALUES (?, ?, ?)";

        try (Connection conn = getDBConnection()) {
            conn.setAutoCommit(false);

            // Validation for withdrawal
            if (type.equalsIgnoreCase("WITHDRAWAL")) {
                try (PreparedStatement checkStmt = conn.prepareStatement(checkBalanceSql)) {
                    checkStmt.setInt(1, accNum);
                    ResultSet rs = checkStmt.executeQuery();
                    if (!rs.next()) {
                        System.err.println("Error: Account #" + accNum + " does not exist.");
                        return;
                    }
                    if (rs.getDouble("balance") < amount) {
                        System.err.println("Error: Insufficient funds. Available balance: $" + rs.getDouble("balance"));
                        return;
                    }
                }
            }

            // Execute Balance Update
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setDouble(1, amount);
                updateStmt.setInt(2, accNum);
                int rows = updateStmt.executeUpdate();
                if (rows == 0) {
                    System.err.println("Error: Account not found.");
                    return;
                }
            }

            // Log Transaction History
            try (PreparedStatement logStmt = conn.prepareStatement(logSql)) {
                logStmt.setInt(1, accNum);
                logStmt.setString(2, type.toUpperCase());
                logStmt.setDouble(3, amount);
                logStmt.executeUpdate();
            }

            conn.commit();
            System.out.println(type + " of $" + amount + " successfully processed!");

        } catch (SQLException e) {
            System.err.println("Operation failed: " + e.getMessage());
        }
    }
    // 4. ADVANCED FEATURE: FUND TRANSFER (SAFE COMMIT/ROLLBACK)
    public static void transferFunds(int senderAcc, int receiverAcc, double amount) {
        if (amount <= 0) {
            System.out.println("Transfer amount must be greater than 0.");
            return;
        }

        String checkBalanceSql = "SELECT balance FROM accounts WHERE account_number = ?";
        String deductSql = "UPDATE accounts SET balance = balance - ? WHERE account_number = ?";
        String addSql = "UPDATE accounts SET balance = balance + ? WHERE account_number = ?";
        String logSql = "INSERT INTO transactions (account_number, transaction_type, amount) VALUES (?, ?, ?)";

        Connection conn = null;

        try {
            conn = getDBConnection();

            // Step A: Disable Auto-Commit to manually manage transaction boundaries
            conn.setAutoCommit(false);

            // Step B: Validate Sender Existence and Balance
            try (PreparedStatement checkStmt = conn.prepareStatement(checkBalanceSql)) {
                checkStmt.setInt(1, senderAcc);
                ResultSet rs = checkStmt.executeQuery();
                if (!rs.next()) {
                    throw new SQLException("Sender account #" + senderAcc + " not found.");
                }
                if (rs.getDouble("balance") < amount) {
                    throw new SQLException("Insufficient funds in sender account.");
                }
            }

            // Step C: Validate Receiver Existence
            try (PreparedStatement checkStmt = conn.prepareStatement(checkBalanceSql)) {
                checkStmt.setInt(1, receiverAcc);
                ResultSet rs = checkStmt.executeQuery();
                if (!rs.next()) {
                    throw new SQLException("Receiver account #" + receiverAcc + " not found.");
                }
            }

            // Step D: Deduct from Sender
            try (PreparedStatement deductStmt = conn.prepareStatement(deductSql)) {
                deductStmt.setDouble(1, amount);
                deductStmt.setInt(2, senderAcc);
                deductStmt.executeUpdate();
            }

            // Step E: Add to Receiver
            try (PreparedStatement addStmt = conn.prepareStatement(addSql)) {
                addStmt.setDouble(1, amount);
                addStmt.setInt(2, receiverAcc);
                addStmt.executeUpdate();
            }

            // Step F: Log 'TRANSFER_OUT' for Sender
            try (PreparedStatement logStmt = conn.prepareStatement(logSql)) {
                logStmt.setInt(1, senderAcc);
                logStmt.setString(2, "TRANSFER_OUT");
                logStmt.setDouble(3, amount);
                logStmt.executeUpdate();
            }

            // Step G: Log 'TRANSFER_IN' for Receiver
            try (PreparedStatement logStmt = conn.prepareStatement(logSql)) {
                logStmt.setInt(1, receiverAcc);
                logStmt.setString(2, "TRANSFER_IN");
                logStmt.setDouble(3, amount);
                logStmt.executeUpdate();
            }

            // Step H: Commit transfer ONLY if every step succeeded
            conn.commit();
            System.out.println("Successfully transferred $" + amount + " from Account #" + senderAcc + " to Account #" + receiverAcc);

        } catch (SQLException e) {
            System.err.println("\n[TRANSFER FAILED]: " + e.getMessage());

            // ROLLBACK MECHANISM: Reverts all balance changes if ANY step fails
            if (conn != null) {
                try {
                    System.err.println("[ROLLBACK TRIGGERED]: Restoring original account balances...");
                    conn.rollback();
                    System.err.println("[ROLLBACK COMPLETE]: No money was deducted.");
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    // 5. TRANSACTION HISTORY MODULE
    public static void printTransactionHistory(int accountNum) {
        String sql = "SELECT * FROM transactions WHERE account_number = ? ORDER BY timestamp DESC";

        try (Connection conn = getDBConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, accountNum);
            ResultSet rs = stmt.executeQuery();

            System.out.println("\n------------------------------------------------------------");
            System.out.println("        TRANSACTION HISTORY FOR ACCOUNT #" + accountNum);
            System.out.println("------------------------------------------------------------");

            boolean hasTransactions = false;
            while (rs.next()) {
                hasTransactions = true;
                System.out.printf("ID: %-4d | Type: %-13s | Amount: $%-8.2f | Date: %s%n",
                        rs.getInt("transaction_id"),
                        rs.getString("transaction_type"),
                        rs.getDouble("amount"),
                        rs.getTimestamp("timestamp"));
            }

            if (!hasTransactions) {
                System.out.println("No records found for this account.");
            }
            System.out.println("------------------------------------------------------------");

        } catch (SQLException e) {
            System.err.println("Error fetching transaction history: " + e.getMessage());
        }
    }
}
