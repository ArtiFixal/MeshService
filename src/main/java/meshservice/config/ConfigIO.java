package meshservice.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Class responsible for all kind of read/write operations related to configs.
 *
 * @author ArtiFixal
 */
public class ConfigIO{

    private final File config;

    public ConfigIO(File config){
        this.config=config;
    }

    public File getConfigFile(){
        return config;
    }

    /**
     * Reads option value from file specified under object cration.
     *
     * @param option Option to search for
     *
     * @return Found option, but never null. If reached EOF but option wasn't
     * found exception is thrown.
     *
     * @throws FileNotFoundException If file to read wasn't found.
     * @throws IOException Any error occurred during reading.
     * @throws OptionNotFoundException If searched option wasn't found in file.
     */
    public String readOptionValue(String option) throws FileNotFoundException,
            IOException,OptionNotFoundException
    {
        try(FileReader r=new FileReader(config);
                BufferedReader br=new BufferedReader(r,512))
        {
            String line;
            while((line=br.readLine())!=null){
                if(line.startsWith(option))
                    return line.substring(line.indexOf(">")+1);
            }
        }
        throw new OptionNotFoundException(option,config.getPath());
    }

    /**
     * Appends option and its value into file specified under object cration.
     *
     * @param option Option name by which it value will be accessed in file.
     * @param value Value corresponding to the option name.
     *
     * @throws IOException Any error occurred during writing.
     */
    public void writeOption(String option,String value) throws IOException
    {
        try(FileWriter w=new FileWriter(config,true);
                BufferedWriter bw=new BufferedWriter(w))
        {
            bw.write("<");
            bw.write(option);
            bw.write(">");
            bw.write(value);
            bw.write('\n');
        }
    }

    /**
     * Writes entire config into file specified under object cration.
     *
     * @param configToWrite Line by line config options.
     *
     * @throws IOException Any error occurred during writing.
     */
    public void writeEntireConfig(String configToWrite) throws IOException{
        try(FileWriter w=new FileWriter(config);
                BufferedWriter bw=new BufferedWriter(w))
        {
            bw.write(configToWrite);
        }
    }
}
