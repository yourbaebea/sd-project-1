package Classes;
import java.io.*;
import java.util.*;

public class ListOfPeople extends Person{

    public ListOfPeople(String username, String password, int phone, String address, int cc, String department,
            boolean admin) {
        super(username, password, phone, address, cc, department, admin);
        //TODO Auto-generated constructor stub
    }

    boolean debug=true; /*just for debugs*/
    
    ArrayList<Person> list;

    /*************************************** RMI TCP functions ************************** */

    public boolean authenticate(String username, String password){
        //for the client to sent the info and the rmi then checks if person has the right login
        return true;
    }



}







