package long.javaapi.ObjectInputStreamInputStreamin

//http://www.java2s.com/Code/JavaAPI/java.io/newObjectInputStreamInputStreamin.htm

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

class Main {

  def main(args:Array[String]) {
    var list:List[String] = List( "A", "B", "C", "D");

    var fos:FileOutputStream = new FileOutputStream("list.ser")
    var oos:ObjectOutputStream = new ObjectOutputStream(fos)
        
    oos.writeObject(list);
    oos.close();

    var ois:ObjectInputStream = new ObjectInputStream(new FileInputStream("list.ser")) //r=2

    var anotherList:List[String] = ois.readObject().asInstanceOf[List[String]]
    ois.close();

    System.out.println(anotherList);
  }
}



/*
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.util.Arrays;
import java.util.List;

public class Main {

  public static void main(String[] a) throws Exception {
    List list = Arrays.asList(new String[] { "A", "B", "C", "D" });

    FileOutputStream fos = new FileOutputStream("list.ser");
    ObjectOutputStream oos = new ObjectOutputStream(fos);
    oos.writeObject(list);
    oos.close();

    FileInputStream fis = new FileInputStream("list.ser");
    ObjectInputStream ois = new ObjectInputStream(fis);
    List anotherList = (List) ois.readObject();
    ois.close();

    System.out.println(anotherList);
  }
}
*/
