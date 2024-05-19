package edu.yu.cs.com1320.project;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;

public class FileInput {
    private final URI url;
    //private final FileInputStream fis;
    private final File file;
    private final String text;

    public FileInput(String pathname) throws FileNotFoundException {
        this.file = new File(pathname);
        this.url = URI.create(pathname);
        //this.fis = new FileInputStream(this.file);
        this.text = "some text";
    }

    /*public FileInputStream getFis() {
        //return fis;
    }*/

    public FileInputStream getNewFis() throws FileNotFoundException {
        return new FileInputStream(this.file);
    }

    public URI getUrl() {
        return url;
    }

    public File getFile(){
        return this.file;
    }
}
