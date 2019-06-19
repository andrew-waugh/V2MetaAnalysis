/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package V2MetaAnalysis;

import VERSCommon.AppError;
import VERSCommon.AppFatal;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import org.xml.sax.Attributes;

/**
 * A Target is a XML tag to be harvested from a VEO. A target has element paths
 * (where to find it in the VEO), a tag (what to label it on output), an
 * optional default (what to output if it is not found), and a set of current
 * values and set of attributes (what has been found in the current VEO).
 * Targets are linked together as a linked list, with a null value at the end.
 *
 * Important: targets are leaf XML entities; non-leaf entities cannot be
 * harvested with this tool. The values of the XML entities are harvested as
 * strings, and multiple values can be captured from one VEO.
 *
 * An elempath is a string representation of the path from the root of the XML
 * document tree to an element tag. It consists of the concatenation of the
 * elements tags separated by '/'.
 *
 * Targets may have multiple elempaths (e.g. a title may be in a FileVEO or a
 * RecordVEO with different elempaths, but will still match the one target).
 *
 * Methods are provided to express the targeted information as an XML document,
 * a JSON structure, or a CSV/TSV file.
 *
 * @author Andrew
 */
public class Target {

    ArrayList<String> elemPath; // the element paths of the XML tag to harvest
    String tag;                 // the tag to label this targe
    ArrayList<String> attributes; // any attributes associated with element
    String deflt;               // a default value to use if it is not found
    ArrayList<String> value;    // the value from the current VEO
    Target next;                // next Target in list
    static boolean firstValue;  // true if outputing the first value in a list of targets

    /**
     * Construct a new Target.
     *
     * @param elemPath the full path of this tag in the VEO
     * @param deflt a default value to use if none are found in the VEO
     * @param tag a handle used to label this Target in the output. If the tag
     * is null, the final element of the elemPath is used.
     * @throws AppFatal if an error occurs that means the program must exit
     */
    public Target(ArrayList<String> elemPath, String deflt, String tag) throws AppFatal {

        // sanity check
        if (elemPath == null) {
            throw new AppFatal("Creating a Target: Passed a null elemPath");
        }
        if (elemPath.size() < 1) {
            throw new AppFatal("Creating a Target: Passed an empty elemPath");
        }

        // create...
        this.elemPath = elemPath;
        if (tag != null) {
            this.tag = tag;
        } else {
            this.tag = elemPath.get(0).substring(elemPath.get(0).lastIndexOf('/') + 1);
            if (this.tag == null || this.tag.equals("")) {
                throw new AppFatal("Couldn't find final tag name in '" + elemPath.get(0) + "'. Does it end in a '/'?");
            }
        }
        this.deflt = deflt;
        value = new ArrayList<>();
        attributes = new ArrayList<>();
    }

    /**
     * Is the given elemPath one of the elemPaths for this target?
     *
     * @param givenElemPath the elempath to check
     * @return return true if the elempath is one of the target's elemPaths.
     */
    public boolean matchElemPath(String givenElemPath) {
        int i;

        for (i = 0; i < elemPath.size(); i++) {
            if (givenElemPath.equals(elemPath.get(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add this Target to the end of the target list
     *
     * @param t target to add
     */
    public void add(Target t) {
        if (next == null) {
            next = t;
        } else {
            next.add(t);
        }
    }

    /**
     * Return the size of this target list.
     *
     * @return size
     */
    public int size() {
        if (next == null) {
            return 1;
        } else {
            return next.size() + 1;
        }
    }

    /**
     * Get the ith target in this target list.
     *
     * @param i count of the target to return (first target is 0)
     * @return target
     */
    public Target get(int i) {
        if (i == 0) {
            return this;
        } else if (next == null) {
            return null;
        } else {
            return next.get(i - 1);
        }
    }

    /**
     * Two magic tag names are 'filename' and 'filepath'. These output the
     * filename or filepath of the VEO being processed.
     *
     * @param p the XML file being processed
     */
    public void setFile(Path p) {
        if (tag.toLowerCase().equals("filepath")) {
            value.add(p.toString());
        } else if (tag.toLowerCase().equals("filename")) {
            value.add(p.getFileName().toString());
        }
        if (next != null) {
            next.setFile(p);
        }
    }

    /**
     * Reset this target for another XML document
     */
    public void clear() {
        value.clear();
        attributes.clear();
        if (next != null) {
            next.clear();
        }
    }

    /**
     * Remember attributes associated with this target. Note that if multiple
     * instances of the target are found in the XML document, all of the
     * attributes will be collected together higgldy piggldy.
     *
     * @param attributes attributes found in the parse
     */
    public void addAttributes(Attributes attributes) {
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                this.attributes.add(attributes.getQName(i) + "=\"" + attributes.getValue(i).trim() + "\"");
            }
        }
    }

    /**
     * Convert this target into a comma (CSV) or tab (TSV) separated line on the
     * output writer. If a value contains the separator, the value is surrounded
     * with double quotes. Multiple values are output separated by a '$$'. Any
     * attributes are ignored.
     *
     * @param w the writer to output the information object
     * @throws java.io.IOException if the writer fails
     */
    public void toCSV(Writer w) throws IOException {
        toXSV(w, ',', true);
    }

    public void toTSV(Writer w) throws IOException {
        toXSV(w, '\t', true);
    }

    private void toXSV(Writer w, char separator, boolean firstValue) throws IOException {
        int i;
        String s;

        if (!firstValue) {
            w.append(separator);
        }
        if (value.isEmpty()) {
            if (deflt != null) {
                w.append(escapeSeparators(deflt, separator));
            }
        } else {
            for (i = 0; i < value.size(); i++) {
                s = value.get(i);
                if (s != null) {
                    w.append(escapeSeparators(s, separator));
                }
                if (i < value.size() - 1) {
                    w.append("$$");
                }
            }
        }
        if (next != null) {
            next.toXSV(w, separator, false);
        }
    }

    /**
     * Strings may contain the separator. If so, wrap the string with double
     * quotes (but escape any double quotes *in* this string).
     *
     * @param in the string to check
     * @param separator the intended separator (comma or tab)
     * @return a safe string
     */
    private String escapeSeparators(String in, char separator) {
        if ((separator == ',' && in.contains(",")) || (separator == '\t' && in.contains("\t"))) {
            if (in.contains("\"")) {
                in = in.replaceAll("\"", "\\\"");
            }
            in = "\"" + in + "\"";
        }
        return in;
    }

    /**
     * Write a standard preamble for TSV output. In practice, write column
     * headings using the tags.
     *
     * @param w output writer
     * @param t list of targets
     * @throws AppFatal if we cannot continue processing
     */
    static public void TSVpreamble(Writer w, Target t) throws AppFatal {
        outputTags(w, t, "\t");
    }

    /**
     * Place holder in case we need to write a postamble for TSV output
     *
     * @param w output writer
     * @throws AppError if we cannot continue processing
     */
    static public void TSVpostamble(Writer w) throws AppError {
    }

    /**
     * Write a standard preamble for CSV output. In practice, write column
     * headings using the tags.
     *
     * @param w output writer
     * @param t list of targets
     * @throws AppFatal if we cannot continue processing
     */
    static public void CSVpreamble(Writer w, Target t) throws AppFatal {
        outputTags(w, t, ",");
    }

    /**
     * Place holder in case we need to write a postamble for CSV output
     *
     * @param w output writer
     * @throws AppError if we cannot continue processing
     */
    static public void CSVpostamble(Writer w) throws AppError {
    }

    /**
     * Output the Target tags as column headings for a TSV or CSV output file.
     *
     * @param w output writer
     * @param t list of targets
     * @param sep separator to use (tab or comma)
     * @throws AppFatal if we cannot continue processing
     */
    static public void outputTags(Writer w, Target t, String sep) throws AppFatal {
        boolean first = true;
        try {
            while (t != null) {
                if (!first) {
                    w.write(sep);
                }
                first = false;
                w.write(t.tag);
                t = t.next;
            }
            w.write("\n");
        } catch (IOException ioe) {
            throw new AppFatal("Failed writing: " + ioe.getMessage());
        }
    }

    /**
     * Convert the string of targeted metadata elements from the VEO into a JSON
     * structure on the output writer. Each targeted element becomes a JSON
     * property on the JSON collection. If multiple values were collected for
     * the targeted element, these are output as a JSON array for the JSON
     * property. If no values were collected for targeted element, the default
     * value (if any) is output. If no values were collected, and no default was
     * specified, a "null" value is output.
     *
     * This is implemented as two related functions. 'toJSON()' is the actual
     * callable, and 'toJSONRest()' is an internal recursive function that
     * processes the list of targeted elements and actually does the work.
     *
     * @param w the writer to output the information object
     * @throws java.io.IOException if the writer fails
     */
    public void toJSON(Writer w) throws IOException {
        w.write("{\n");
        toJSONRest(w, true);
        w.write("}");
    }

    private void toJSONRest(Writer w, boolean firstValue) throws IOException {
        int i;
        String s;

        if (!firstValue) {
            w.append(",\n");
        }
        w.append(" \"");
        w.append(tag);
        w.append("\": ");
        if (value.isEmpty()) {
            if (deflt != null) {
                w.append("\"" + deflt + "\"");
            } else {
                w.append("\"null\"");
            }
        } else if (value.size() == 1) {
            w.append("\"");
            s = value.get(0);
            if (s != null) {
                w.append(s);
            } else {
                w.append("null");
            }
            w.append("\"");
        } else {
            w.append("[\n");
            for (i = 0; i < value.size(); i++) {
                w.append("  {\"");
                s = value.get(i);
                if (s != null) {
                    w.append(s);
                } else {
                    w.append("null");
                }
                w.append("\"}");
                if (i < value.size() - 1) {
                    w.append(",\n");
                }
            }
            w.append("]");
        }
        if (next != null) {
            next.toJSONRest(w, false);
        }
    }

    /**
     * Write a standard preamble for JSON output. In practice start a single
     * JSON property 'report'.
     *
     * @param w output writer
     * @throws AppError if this VEO has to be abandoned
     */
    static public void JSONpreamble(Writer w) throws AppError {
        try {
            w.append("{\"report\":[");
        } catch (IOException ioe) {
            throw new AppError("Failed writing JSON output");
        }
    }

    /**
     * Write a standard postamble for JSON output. In practice end the single
     * JSON property 'report'.
     *
     * @param w output writer
     * @throws AppError if this VEO has to be abandoned
     */
    static public void JSONpostamble(Writer w) throws AppError {
        try {
            w.append("]}");
        } catch (IOException ioe) {
            throw new AppError("Failed writing JSON output");
        }
    }

    /**
     * Convert this information object into XML on the output writer
     *
     * @param w the writer to output the information object
     * @throws java.io.IOException if the writer fails
     */
    public void toXML(Writer w) throws IOException, AppError {
        w.write("<Report>\n");
        toXMLRest(w, true);
        w.write("\n</Report>");
    }

    public void toXMLRest(Writer w, boolean firstValue) throws IOException, AppError {
        int i;

        if (!firstValue) {
            w.append("\n");
        }

        // if value is empty, write the default if present. Otherwise, write
        // the list of values as a sequence of values.
        if (value.isEmpty()) {
            outputElement(w, tag, deflt);
        } else {
            for (i = 0; i < value.size(); i++) {
                outputElement(w, tag, value.get(i));
                if (i < value.size() - 1) {
                    w.append("\n");
                }
            }
        }
        
        // recurse
        if (next != null) {
            next.toXMLRest(w, false);
        }
    }

    /**
     * Output a target as an XML element.
     * 
     * @param w output writer
     * @param tag tag of XML element
     * @param value value of XML element (may be null)
     * @throws IOException
     */
    private void outputElement(Writer w, String tag, String value) throws IOException {
        
        // start tag
        w.append(" <");
        w.append(tag);

        // include attributes (if any)
        outputAttrs(w);
        if (value != null) {
            w.append(">");
            w.append(xmlEncode(value));
            w.append("</");
            w.append(tag);
            w.append(">");
        } else {
            w.append("/>");
        }
    }

    /**
     * Output the attributes as XML
     *
     * @param w output writer
     * @throws IOException the writing failied
     */
    private void outputAttrs(Writer w) throws IOException {
        int i;

        if (attributes != null) {
            for (i = 0; i < attributes.size(); i++) {
                w.append(" ");
                w.append(attributes.get(i));
            }
        }
    }

    static public void XMLpreamble(Writer w) throws AppError {
        try {
            w.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n");
            w.append("<report>\n");
        } catch (IOException ioe) {
            throw new AppError("Failed writing JSON output");
        }
    }

    static public void XMLpostamble(Writer w) throws AppError {
        try {
            w.append("</report>\n");
        } catch (IOException ioe) {
            throw new AppError("Failed writing JSON output");
        }
    }

    /**
     * XML encode string
     *
     * Make sure any XML special characters in a string are encoded
     *
     * @param in the String to encode
     * @return the encoded string
     */
    public String xmlEncode(String in) {
        StringBuffer out;
        int i;
        char c;

        if (in == null) {
            return null;
        }
        out = new StringBuffer();
        for (i = 0; i < in.length(); i++) {
            c = in.charAt(i);
            switch (c) {
                case '&':
                    if (!in.regionMatches(true, i, "&amp;", 0, 5)
                            && !in.regionMatches(true, i, "&lt;", 0, 4)
                            && !in.regionMatches(true, i, "&gt;", 0, 4)
                            && !in.regionMatches(true, i, "&quot;", 0, 6)
                            && !in.regionMatches(true, i, "&apos;", 0, 6)) {
                        out.append("&amp;");
                    }
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&apos;");
                    break;
                default:
                    out.append(c);
                    break;
            }
        }
        return (out.toString());
    }
}
