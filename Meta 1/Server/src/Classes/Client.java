package Classes;
import java.io.Serializable;

public class Client implements Serializable {

    public String username;
    private boolean admin;
    private String current;
    public boolean logged= false;

    public Client() {        
    }

    public void setUsername(String username) {
        this.username=username;
    }

    public void setLogged(boolean logged) {
        this.logged = logged;
    }

    public void setCurrent(String dir){
        this.current=dir;
    }

    public String getCurrent() {
        return current;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isAdmin() {
        return admin;
    }
}
