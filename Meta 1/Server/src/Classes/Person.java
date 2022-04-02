package Classes;
import java.io.Serializable;

public class Person implements Serializable {

    public boolean admin;

    private final String username;
    private String password; /*cant be final we need to be able to change it*/
    private final int phone;
    private final String address;
    private final int cc;
    private final String department;

    public int getCc() {
        return cc;
    }

    public String getAddress() {
        return address;
    }

    public int getPhone() {
        return phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDepartment() {
        return department;
    }

    public Person(String username, String password, int phone, String address, int cc, String department, boolean admin) {
        this.username = username;
        this.password=password;
        this.phone=phone;
        this.address=address;
        this.cc= cc;
        this.department=department;
        this.admin = admin;
        
    }

    public String getUsername() {
        return username;
    }

    public boolean checkPassword(String pass){
        return this.password.equals(pass);
    }

    public boolean changePassword(String pass){
        this.password=pass;
        return true;
    }

    @Override
    public String toString() {
        return "Person{" + "username='" + username + '\'' + '}';
    }
}
