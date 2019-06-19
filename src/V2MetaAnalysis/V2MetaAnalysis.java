/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package V2MetaAnalysis;

import VERSCommon.AppError;
import VERSCommon.AppFatal;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extract information from VEOs and express this information in a variety of
 * formats for further processing.
 *
 * The mandatory command line arguments are:
 * <ul>
 * <li> '-cf controlFile': a file specifying which elements to extract.
 * Specified elements may be non-leaf elements, in which case the element and
 * all its subelements are extracted.</li>
 * <li> list of VEOs (or directories of VEOs) to process.</li>
 * </ul>
 * <p>
 * The information extracted can be expressed as XML, JSON, tab separated
 * variables (TSV) or comma separated variables (CSV). The following command
 * line arguments are used to select the output format:
 * <ul>
 * <li> '-xml': output as XML
 * <li> '-json': output as JSON. The XML element names become the JSON property
 * names. Where an XML element is repeated, the XML element appears once and the
 * multiple values appear as an JSON array. XML attributes are converted into
 * JSON properties (except if the attribute is a simple element).
 * <li> '-csv': output as CSV. The simple values are exported in the order they
 * appear in the VEO separated by commas. BEWARE: if an element occurs multiple
 * times, it will appear in the output multiple times.
 * <li> '-tsv': output as TSV. The simple values are exported in the order they
 * appear in the VEO separated by tabs. BEWARE: if an element occurs multiple
 * times, it will appear in the output multiple times.
 * </ul>
 * If the output format is not explicitly specified in the command line, it is
 * inferred if possible from the output file name (e.g. a request to produce the
 * output file 'output.json' will produce a JSON file as you would expect).
 * However if you specify the output to be '-csv' and the output file to be
 * 'output.json', the output will be a CSV file.
 * <p>
 * The selected output can either be expressed as a set of files (one for each
 * VEO), or a single combined file containing the results for all VEOs. In the
 * latter case, the single file can be written to standard out, or to a
 * specified file. A number of command line arguments control the output:
 * <ul>
 * <li> '-stdout': write all the output to the standard output. If this is
 * specified the '-od' and '-o' commands have not effect.
 * <li> '-od directory': produce the output files in the specified directory
 * (has no effect if '-stdout' is specified)
 * <li> '-o file': write all the output into a single specified file (has no
 * effect if '-stdout' is specified). The output file is created in directory
 * specified by the '-od' command (if present), or the current working directory
 * (if not).
 * <ul>
 * If neither the '-stdout' or '-o' commands are present, each input VEO will
 * produce a single output file. The name of the file will be the same of the
 * input file with the appropriate file extension (e.g. processing 'test.veo' as
 * a JSON file will produce 'test.json'). The output files are created in the
 * directory specified by the '-od' command (if present), or in the current
 * working directory (if not).
 * <p>
 * The other optional command line arguments are:
 * <ul>
 * <li>'-c': chatty mode. Report on stderr when a new VEO is commenced.
 * <li>'-v': verbose output. Include additional details in the report generated
 * by the '-r' option.</li>
 * <li>'-d': debug output. Include lots more detail - mainly intended to debug
 * problems with the program.</li>
 * </ul>
 *
 * @author Andrew Waugh
 */
public class V2MetaAnalysis {

    String classname = "V2MetaAnalysis";
    Path controlFile;   // file that controls processing of the VEOs
    boolean chatty;     // true if report when starting a new VEO
    boolean error;      // true if produce a summary error report
    boolean debug;      // true if debugging information is to be generated
    boolean verbose;    // true if verbose descriptions are to be generated
    boolean norec;      // true if asked to not complain about missing recommended metadata elements
    boolean hasErrors;  // true if VEO had errors
    ArrayList<String> fileOrDirectories; // The fileOrDirectories to process
    Target targets;     // the metadata elements to pick from a VEO
    V2Parser pv;        // parser and processor for VEOs
    Information info;   // results of processing VEO
    boolean firstVEO;   // true if this is the first VEO to be processed
    private final static Logger LOG = Logger.getLogger("V2MetaAnalysis.V2MetaAnalysis");

    // where the output is to go
    Path outputDir;     // directory in which the output files are to go

    enum OutputType {   // types of output produced
        UNDEFINED, // will be implicitly defined by file name
        XML, // output will be an XML file
        JSON, // output will be a JSON file
        TSV, // output will be a text file with tab separated values
        CSV             // output will be a text file with comma separared values
    }
    OutputType outputType; // how the output is to be expressed
    boolean groupOutput;// true if all output is to go to one file
    boolean stdout;     // write output to standard out
    Path outputFile;    // file in which the output is to go
    Writer output;      // where to place output
    Writer commentary;  // where to place errors and diagnostics

    /**
     * Initialise the analysis regime for the headless mode. In this mode,
     * VEOAnalysis is called by another program to unpack and validate the VEO.
     *
     * @param controlFile directory in which VERS3 schema information is found
     * @param outputDir directory in which the VEO will be unpacked
     * @param hndlr where to send the LOG reports
     * @param chatty true if report when starting a new VEO
     * @param error true if produce a summary error report
     * @param debug true if debugging information is to be generated
     * @param verbose true if verbose descriptions are to be generated
     * @param norec true if asked to not complain about missing recommended
     * metadata elements
     * @throws VERSCommon.AppFatal if cannot continue processing
     */
    public V2MetaAnalysis(Path controlFile, Path outputDir,
            Handler hndlr, boolean chatty, boolean error,
            boolean debug, boolean verbose, boolean norec) throws AppFatal {
        Handler h[];
        int i;

        // remove any handlers associated with the LOG & LOG messages aren't to
        // go to the parent
        h = LOG.getHandlers();
        for (i = 0; i < h.length; i++) {
            LOG.removeHandler(h[i]);
        }
        LOG.setUseParentHandlers(false);

        // add LOG handler from calling program
        LOG.addHandler(hndlr);

        // default logging
        LOG.getParent().setLevel(Level.WARNING);
        LOG.setLevel(null);

        if (controlFile == null || !Files.isRegularFile(controlFile)) {
            throw new AppFatal("Specified control file is null or is not a file");
        }
        this.controlFile = controlFile;
        if (outputDir == null || !Files.isDirectory(outputDir)) {
            throw new AppFatal("Specified output directory is null or is not a directory");
        }
        this.outputFile = outputDir;
        this.chatty = chatty;
        this.error = error;
        this.verbose = verbose;
        if (verbose) {
            LOG.getParent().setLevel(Level.INFO);
        }
        this.debug = debug;
        if (debug) {
            LOG.getParent().setLevel(Level.FINE);
        }
        this.norec = norec;
        fileOrDirectories = null;
        targets = null;
        hasErrors = false;
        readTargets(controlFile);
        pv = new V2Parser(targets);
        info = null;
    }

    /**
     * Initialise the analysis regime.
     *
     * @param args the command line arguments
     * @throws VERSCommon.AppFatal if something goes wrong
     */
    public V2MetaAnalysis(String args[]) throws AppFatal {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s%n");
        LOG.getParent().setLevel(Level.WARNING);
        LOG.setLevel(null);
        initialise();
        configure(args);
        readTargets(controlFile);
        pv = new V2Parser(targets);
        info = null;
    }

    /**
     * Initialise the global variables
     */
    private void initialise() {
        targets = null;
        controlFile = null;
        outputDir = Paths.get(".");
        outputType = OutputType.UNDEFINED;
        stdout = false;
        groupOutput = false;
        outputFile = null;
        output = null;
        commentary = null;
        chatty = false;
        error = false;
        debug = false;
        verbose = false;
        norec = false;
        firstVEO = true;
        fileOrDirectories = new ArrayList<>();
    }

    /**
     * This method configures the VEO analysis from the arguments on the command
     * line. See the comment at the start of this file for the command line
     * arguments.
     *
     * @param args[] the command line arguments
     * @throws AppFatal if any errors are found in the command line arguments
     */
    private void configure(String args[]) throws AppFatal {
        int i;
        String usage = "AnalyseVEOs [-e] [-r] [-u] [-v] [-d] [-c] -cf controlFile [-od outputDir] [-xml|-json|-csv|-tsv] [-o outputFile|-stdout] [files*]";

        // process command line arguments
        i = 0;
        try {
            while (i < args.length) {
                switch (args[i].toLowerCase()) {
                    // if chatty mode...
                    case "-c":
                        chatty = true;
                        i++;
                        LOG.log(Level.INFO, "Report when staring new VEO mode is selected");
                        break;

                    // if debugging...
                    case "-d":
                        debug = true;
                        i++;
                        LOG.getParent().setLevel(Level.FINE);
                        LOG.log(Level.INFO, "Debug mode is selected");
                        break;

                    // if verbose...
                    case "-v":
                        verbose = true;
                        i++;
                        LOG.getParent().setLevel(Level.INFO);
                        LOG.log(Level.INFO, "Verbose output is selected");
                        break;

                    // produce summary report containing errors and warnings
                    case "-e":
                        error = true;
                        i++;
                        LOG.log(Level.INFO, "Summary report mode is selected");
                        break;

                    // get output directory
                    case "-od":
                        i++;
                        outputDir = checkFile("output directory", args[i], true);
                        LOG.log(Level.INFO, "Output directory is ''{0}''", outputDir.toString());
                        i++;
                        break;

                    // output type is XML
                    case "-xml":
                        i++;
                        outputType = OutputType.XML;
                        LOG.log(Level.INFO, "Output type is XML");
                        break;

                    // output type is JSON
                    case "-json":
                        i++;
                        outputType = OutputType.JSON;
                        LOG.log(Level.INFO, "Output type is JSON");
                        break;

                    // output type is JSON
                    case "-tsv":
                        i++;
                        outputType = OutputType.TSV;
                        LOG.log(Level.INFO, "Output type is text with tab separated valuses (TSV)");
                        break;

                    // output type is JSON
                    case "-csv":
                        i++;
                        outputType = OutputType.CSV;
                        LOG.log(Level.INFO, "Output type is text with comma separated valuses (CSV)");
                        break;

                    // get output file
                    case "-stdout":
                        i++;
                        stdout = true;
                        groupOutput = true;
                        LOG.log(Level.INFO, "Write output to standard out");
                        break;

                    // get output file
                    case "-o":
                        i++;
                        outputFile = Paths.get(args[i].replaceAll("\\\\", "/"));
                        groupOutput = true;
                        LOG.log(Level.INFO, "Output file is ''{0}''", outputFile.toString());
                        i++;
                        break;

                    // get control file
                    case "-cf":
                        i++;
                        controlFile = checkFile("control file", args[i], false);
                        LOG.log(Level.INFO, "Control File is ''{0}''", controlFile.toString());
                        i++;
                        break;

                    // otherwise, check if it starts with a '-' and complain, otherwise assume it is a VEO pathname
                    default:
                        if (args[i].startsWith("-")) {
                            throw new AppFatal(classname, 2, "Unrecognised argument '" + args[i] + "'. Usage: " + usage);
                        } else {
                            fileOrDirectories.add(args[i]);
                            i++;
                        }
                }
            }
        } catch (ArrayIndexOutOfBoundsException ae) {
            throw new AppFatal(classname, 3, "Missing argument. Usage: " + usage);
        }

        // check to see that user specified a control file
        if (controlFile == null) {
            throw new AppFatal(classname, 4, "No control file specified. Usage: " + usage);
        }

        // check that at least one VEO or directory was specified
        if (fileOrDirectories.isEmpty()) {
            throw new AppFatal(classname, 6, "No VEOs or directories were specified. Usage: " + usage);
        }

        // if stdOut and an output file named, complain
        if (stdout && outputFile != null) {
            throw new AppFatal(classname, 5, "Requested output to standard output and a specific file. Usage: " + usage);
        }

        // if no output format specified, see if you can infer it from the file
        // extension of the specified output file
        if (outputType == OutputType.UNDEFINED) {
            if (outputFile != null) {
                String s = outputFile.getFileName().toString();
                i = s.lastIndexOf(".");
                if (i != -1) {
                    s = s.substring(i + 1).toLowerCase();
                    switch (s) {
                        case "xml":
                            outputType = OutputType.XML;
                            LOG.log(Level.INFO, "Output type is XML (set from output file name)");
                            break;
                        case "json":
                            outputType = OutputType.JSON;
                            LOG.log(Level.INFO, "Output type is JSON (set from output file name)");
                            break;
                        case "tsv":
                            outputType = OutputType.TSV;
                            LOG.log(Level.INFO, "Output type is text with tab separated valuses (TSV) (set from output file name)");
                            break;
                        case "csv":
                            outputType = OutputType.CSV;
                            LOG.log(Level.INFO, "Output type is text with comma separated valuses (CSV) (set from output file name)");
                            break;
                        default:
                            break;
                    }
                }
            }
        }
        if (outputType == OutputType.UNDEFINED) {
            throw new AppFatal(classname, 5, "No output type (XML, JSON, CSV or TSV) defined and cannot be inferred from output file. Usage: " + usage);
        }
    }

    /**
     * Check a file to see that it exists and is of the correct type (regular
     * file or directory). The program terminates if an error is encountered.
     *
     * @param type a String describing the file to be opened
     * @param name the file name to be opened
     * @param isDirectory true if the file is supposed to be a directory
     * @throws AppFatal if the file does not exist, or is of the correct type
     * @return the File opened
     */
    private Path checkFile(String type, String name, boolean isDirectory) throws AppFatal {
        Path p;

        String safe = name.replaceAll("\\\\", "/");
        p = Paths.get(safe);

        if (!Files.exists(p)) {
            throw new AppFatal(classname, 6, type + " '" + p.toAbsolutePath().normalize().toString() + "' does not exist");
        }
        if (isDirectory && !Files.isDirectory(p)) {
            throw new AppFatal(classname, 7, type + " '" + p.toAbsolutePath().normalize().toString() + "' is a file not a directory");
        }
        if (!isDirectory && Files.isDirectory(p)) {
            throw new AppFatal(classname, 8, type + " '" + p.toAbsolutePath().normalize().toString() + "' is a directory not a file");
        }
        // LOG.log(Level.INFO, "{0} is ''{1}''", new Object[]{type, p.toAbsolutePath().toString()});
        return p;
    }

    /**
     * Process the files and directories listed in the command line argument...
     *
     * @throws VERSCommon.AppFatal if an error occurred that meant further
     * processing was pointless
     */
    public void processVEOs() throws AppFatal {
        int i;
        String name, safe;
        Path file;

        // if producing one output file (i.e. user specified stdout or a specific output file), open it...
        if (groupOutput) {

            // if a specific output file has been specified, open it, otherwise use stdout
            file = null;
            if (outputFile != null) {
                if (!outputFile.isAbsolute()) {
                    file = outputDir.resolve(outputFile);
                } else {
                    file = outputFile;
                }
            }
            try {
                output = openOutput(file);
            } catch (FileNotFoundException fnfe) {
                throw new AppFatal("Couldn't create output file: " + fnfe.getMessage());
            } catch (AppError ae) {
                throw new AppFatal(ae.getMessage());
            }
        } else {
            output = null;
        }

        // go through the list of VEOs
        firstVEO = true;
        for (i = 0; i < fileOrDirectories.size(); i++) {
            name = fileOrDirectories.get(i);
            System.out.println("Processing: " + name);
            if (name == null) {
                continue;
            }
            safe = name.replaceAll("\\\\", "/");
            try {
                file = Paths.get(safe);
            } catch (InvalidPathException ipe) {
                System.out.println("File or directory name '" + safe + "' is invalid: " + ipe.getMessage() + " Ignored.");
                continue;
            }
            processFileOrDirectory(file, output);
        }

        // producing one output file, close it...
        if (groupOutput) {
            try {
                closeOutput(output);
            } catch (AppError ae) {
                throw new AppFatal(ae.getMessage());
            }
        }
    }

    /**
     * Recurse through any directory structure
     *
     * @param file file to be processed (could be a directory)
     * @param output where the output is to go (null if individual output files
     * are to be generated)
     * @throws VEOError if a fatal error occurred
     */
    private void processFileOrDirectory(Path file, Writer output) throws AppFatal {
        Information i;
        String s;

        if (Files.isDirectory(file)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(file)) {
                for (Path p : ds) {
                    processFileOrDirectory(p, output);
                }
            } catch (IOException e) {
                LOG.log(Level.INFO, ("Failed to process directory '" + file.toString() + "': " + e.getMessage()));
            }
        } else {
            s = file.toString().toLowerCase();
            if (Files.isRegularFile(file) && (s.endsWith(".veo") || s.endsWith(".xml"))) {
                i = null;
                try {
                    processVEO(file, output);
                } catch (AppError ae) {
                    LOG.log(Level.INFO, ("Failed processing file '" + file.toString() + "': " + ae.toString()));
                    return;
                }
                if (info == null) {
                    info = i;
                } else {
                    // info.append(i);
                }
            } else {
                LOG.log(Level.INFO, ("Did not process file '" + file.toString() + "' as it was not a V2 VEO or an XML"));
            }
        }
    }

    /**
     * We've got a VEO to process...
     *
     * @param file VEO file
     * @param output place where output is to be placed
     * @throws AppFatal if the error is so bad the program has to exit
     * @throws AppError if the error means this VEO needs to be abandoned
     */
    private void processVEO(Path file, Writer output) throws AppFatal, AppError {
        Writer w;
        String filename, ext;
        int i;

        LOG.log(Level.INFO, ("Processing " + file.toString()));

        // no output file specified, create one for just this VEO in the output
        // directory. The output file is based on the VEO file name, but we
        // test to make sure that we are not overwriting the original VEO
        if (output == null) {
            if (outputDir == null) {
                throw new AppFatal("Attempting to produce a directory of output without specifying output directory");
            }

            filename = file.getFileName().toString();
            i = filename.lastIndexOf(".");
            if (i != -1) {
                filename = filename.substring(0, i);
            }
            switch (outputType) {
                case XML:
                    ext = "xml";
                    break;
                case JSON:
                    ext = "json";
                    break;
                case TSV:
                    ext = "tsv";
                    break;
                case CSV:
                    ext = "csv";
                    break;
                default:
                    throw new AppFatal("An output type must be specified.");
            }
            Path p = outputDir.resolve(filename + "." + ext);
            LOG.log(Level.INFO, ("New file " + p.toString()));
            try {
                if (Files.exists(p) && Files.isSameFile(p, file)) {
                    throw new AppError("The input file (" + file.toString() + ") is the same as the output file (" + p.toString() + ")");
                }
            } catch (IOException ioe) {
                throw new AppFatal("Fatal error when comparing input and output file names: " + ioe.toString());
            }
            try {
                w = openOutput(p);
            } catch (FileNotFoundException fnfe) {
                throw new AppError("Couldn't create output file: " + fnfe.getMessage());
            }
        } else {
            w = output;
        }
        
        // clear the targets
        targets.clear();
        
        // check to see if we are output the filepath or filename
        targets.setFile(file.normalize().toAbsolutePath());

        pv.parse(file);
        try {
            switch (outputType) {
                case XML:
                    if (!firstVEO) {
                        w.append("\n");
                    }
                    targets.toXML(w);
                    break;
                case JSON:
                    if (!firstVEO) {
                        w.append(",\n");
                    }
                    targets.toJSON(w);
                    break;
                case TSV:
                    if (!firstVEO) {
                        w.append("\n");
                    }
                    targets.toTSV(w);
                    break;
                case CSV:
                    if (!firstVEO) {
                        w.append("\n");
                    }
                    targets.toCSV(w);
                    break;
                default:
                    throw new AppFatal("An output type must be specified.");
            }
            firstVEO = false;
        } catch (IOException ioe) {
            throw new AppError("Failed writing output: " + ioe.getMessage());
        } // however we exit (normally or via an AppFatal exception), manage
        // the output stream. If using an output stream, flush it. Then, if
        // NOT writing to standard out, close it.
        finally {
            try {
                w.flush();
            } catch (IOException iow) {
                /* ignore */ }
            if (output == null) {
                closeOutput(w);
            }
        }
    }

    /**
     * Open an output writer to write results of analysis
     *
     * @return the output writer
     * @throws FileNotFoundException if the output file couldn't be created
     * @throws AppFatal shouldn't happen, but something really bad occurred
     */
    private Writer openOutput(Path file) throws AppFatal, AppError, FileNotFoundException {
        OutputStreamWriter osw;
        OutputStream os;
        Path p;

        // if a specific output file has been specified, open it, otherwise use stdout
        if (file != null) {
            os = new FileOutputStream(file.toFile());

            // otherwise, if output to standard out requested, use that
        } else if (stdout) {
            os = System.out;
        } else {
            throw new AppFatal("Attempting to produce the group output without specifying an output file or using std out");
        }

        // set output encoding and buffering
        try {
            osw = new OutputStreamWriter(os, "UTF-8");
        } catch (UnsupportedEncodingException uee) {
            throw new AppFatal("UTF-8 is an unsupported encoding exception!");
        }
        output = new BufferedWriter(osw);

        // write preamble
        switch (outputType) {
            case XML:
                Target.XMLpreamble(output);
                break;
            case JSON:
                Target.JSONpreamble(output);
                break;
            case TSV:
                Target.TSVpreamble(output, targets);
                break;
            case CSV:
                Target.CSVpreamble(output, targets);
                break;
            default:
                throw new AppFatal("An output type must be specified.");
        }

        return output;
    }

    /**
     * Flush and close the output writer
     *
     * @param output the output to close
     */
    private void closeOutput(Writer output) throws AppError {

        // write preamble
        switch (outputType) {
            case XML:
                Information.XMLpostamble(output);
                break;
            case JSON:
                Information.JSONpostamble(output);
                break;
            case TSV:
                Information.TSVpostamble(output);
                break;
            case CSV:
                Information.CSVpostamble(output);
                break;
            default:
                throw new AppError("An output type must be specified.");
        }

        // flush and close
        if (output != null) {
            try {
                output.flush();
                output.close();
            } catch (IOException ioe) {
                /* ignore */ }
        }
    }

    /**
     * Read a file containing a list of metadata elements that are interesting.
     * Each line specifies one metadata element in a path format. The output
     * order reflects the order of the metadata elements. Lines that begin with
     * an '!' are comments
     *
     * @param controlFile the file containing the list of elements to harvest
     * @throws AppFatal if the file could not be read
     */
    static String[][] prefixes = {
        {"fileVEO", "vers:VERSEncapsulatedObject/vers:SignedObject/vers:ObjectContent/vers:File"},
        {"fileVEO", "vers:VERSEncapsulatedObject/vers:SignedObject/vers:ObjectContent/vers:ModifiedVEO/vers:RevisedVEO/vers:SignedObject/vers:ObjectContent/vers:File"},
        {"recordVEO", "vers:VERSEncapsulatedObject/vers:SignedObject/vers:ObjectContent/vers:Record"},
        {"recordVEO", "vers:VERSEncapsulatedObject/vers:SignedObject/vers:ObjectContent/vers:ModifiedVEO/vers:RevisedVEO/vers:SignedObject/vers:ObjectContent/vers:Record"},
        {"VEOMetadata", "vers:VERSEncapsulatedObject/vers:SignedObject/vers:ObjectContent/vers:File/vers:FileMetadata"},
        {"VEOMetadata", "vers:VERSEncapsulatedObject/vers:SignedObject/vers:ObjectContent/vers:ModifiedVEO/vers:RevisedVEO/vers:SignedObject/vers:ObjectContent/vers:File/vers:FileMetadata"},
        {"VEOMetadata", "vers:VERSEncapsulatedObject/vers:SignedObject/vers:ObjectContent/vers:Record/vers:RecordMetadata"},
        {"VEOMetadata", "vers:VERSEncapsulatedObject/vers:SignedObject/vers:ObjectContent/vers:ModifiedVEO/vers:RevisedVEO/vers:SignedObject/vers:ObjectContent/vers:Record/vers:RecordMetadata"}
    };

    private void readTargets(Path controlFile) throws AppFatal {
        String method = "readMetaElems";
        String line, deflt, tag;
        String tokens[];
        int i;
        boolean rewritten;
        Target t;
        ArrayList<String> elemPath;

        // open controlFile for reading
        try (
                FileReader fr = new FileReader(controlFile.toString());
                BufferedReader br = new BufferedReader(fr)) {

            // go through controlFile line by line, copying patterns into hash map
            while ((line = br.readLine()) != null) {
                line = line.trim();

                // ignore lines that do begin with a '!' - these are comment lines
                if (line.length() == 0 || line.charAt(0) == '!') {
                    continue;
                }

                // split line into tokens
                tokens = line.split("\t");

                // to simplify the writing of the element to match, we define a
                // set of prefixes that can be used. If a prefix has been used,
                // expand it out. Note that there may be more than one matching
                // prefix definition.
                elemPath = new ArrayList<>();
                rewritten = false;
                for (i = 0; i < prefixes.length; i++) {
                    if (tokens[0].startsWith(prefixes[i][0])) {
                        elemPath.add(prefixes[i][1] + tokens[0].substring(prefixes[i][0].length()));
                        rewritten = true;
                    }
                }

                // if not rewritten, add the element just read
                if (!rewritten) {
                    elemPath.add(tokens[0]);
                }

                // see if a default is specified
                if (tokens.length < 2) {
                    deflt = null;
                } else {
                    deflt = tokens[1];
                }
                
                // see if an alternative elemPath is specified
                if (tokens.length < 3) {
                    tag = null;
                } else {
                    tag = tokens[2];
                }

                // add new target
                t = new Target(elemPath, deflt, tag);
                if (targets == null) {
                    targets = t;
                } else {
                    targets.add(t);
                }
            }
        } catch (AppFatal ae) {
            throw new AppFatal(classname, method, 3, "Failed reading control file: "+ae.getMessage());
        } catch (FileNotFoundException e) {
            throw new AppFatal(classname, method, 2, "Failed to open template file '" + controlFile.toAbsolutePath().toString() + "'" + e.toString());
        } catch (IOException ioe) {
            throw new AppFatal(classname, method, 1, "unexpected error: " + ioe.toString());
        }
        for (i = 0; i < targets.size(); i++) {
            System.out.println("Looking for " + targets.get(i).elemPath);
        }
    }

    /**
     * Main entry point for the VEOAnalysis program.
     *
     * @param args A set of command line arguments. See the introduction for
     * details.
     */
    public static void main(String args[]) {
        V2MetaAnalysis v2ma;

        try {
            v2ma = new V2MetaAnalysis(args);
            v2ma.processVEOs();
        } catch (AppFatal e) {
            LOG.log(Level.SEVERE, e.getMessage());
        }
    }
}
