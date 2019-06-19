/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package V2MetaAnalysis;

import VERSCommon.AppError;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import org.xml.sax.Attributes;

/**
 * This element represents a piece of Information from an XML file
 *
 * @author Andrew
 */
public class Information {

    String elemPath;    // path of this element from the root
    String tag;         // just the last part
    String[] attributes;    // any attributes associated with element
    boolean harvest;    // true if we are harvest the value from this element
    String value;       // harvested (simple) value
    ArrayList<Information> children; // harvested (complex) value
    Information sameTag; // children that have the same tag name
    boolean output;

    /**
     * constructor given path and attributes of this element
     *
     * @param elementPath
     * @param attributes
     */
    public Information(String elementPath, Attributes attributes) {
        this.elemPath = elementPath;
        if (attributes != null) {
            this.attributes = new String[attributes.getLength()];
            for (int i = 0; i < attributes.getLength(); i++) {
                this.attributes[i] = attributes.getQName(i) + "=\"" + attributes.getValue(i).trim() + "\"";
            }
        }
        tag = elemPath.substring(elemPath.lastIndexOf('/') + 1);
        harvest = false;
        value = null;
        children = new ArrayList<>();
        sameTag = null;
        output = false;
    }

    /**
     * Free the information
     */
    public void free() {
        int i;

        elemPath = null;
        attributes = null;
        harvest = false;
        value = null;
        for (i = 0; i < children.size(); i++) {
            children.get(i).free();
        }
        children.clear();
        children = null;
    }

    /**
     * Test to see if this element has any subelements
     *
     * @return true if this element has any subelements
     */
    public boolean hasChildren() {
        return (!children.isEmpty());
    }

    /**
     * Add a new child (sub) element
     *
     * @param e
     */
    public void addChild(Information e) {
        int i;
        Information child;

        children.add(e);
        for (i = children.size() - 2; i >= 0; i--) {
            child = children.get(i);
            if (e.tag.equals(child.tag)) {
                child.sameTag = e;
                break;
            }
        }
    }

    public void append(Information e) {
        children.add(e);
    }

    /**
     * Produce a string describing this element
     *
     * @return
     */
    @Override
    public String toString() {
        return toString(0);
    }

    public String toString(int depth) {
        int i;
        StringBuilder sb = new StringBuilder();

        for (i = 0; i < depth; i++) {
            sb.append(' ');
        }
        sb.append("{");
        if (elemPath != null) {
            sb.append(elemPath.substring(elemPath.lastIndexOf('/') + 1));
        } else {
            sb.append("<null>");
        }
        sb.append(": ");
        if (value != null) {
            sb.append("'" + value + "'}\n");
        } else if (!children.isEmpty()) {
            for (i = 0; i < children.size(); i++) {
                sb.append("\n");
                sb.append(children.get(i).toString(depth + 1));
            }
            for (i = 0; i < depth; i++) {
                sb.append(' ');
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    /**
     * Convert this information object into a comma or tab separated line on the
     * output writer. If a value contains the separator, the value is surrounded
     * with double quotes.
     *
     * @param w the writer to output the information object
     * @throws java.io.IOException if the writer fails
     */
    static boolean firstValue;

    public void toCSV(Writer w) throws IOException {
        firstValue = true;
        toXSV(w, ',');
    }

    public void toTSV(Writer w) throws IOException {
        firstValue = true;
        toXSV(w, '\t');
    }

    private void toXSV(Writer w, char separator) throws IOException {
        int i;

        if (value != null) {
            if (!firstValue) {
                w.append(separator);
            }
            if ((separator == ',' && value.contains(",")) || (separator == '\t' && value.contains("\t"))) {
                w.append("\"");
                if (value.contains("\"")) {
                    w.append(value.replaceAll("\"", "\\\""));
                } else {
                    w.append(value);
                }
                w.append("\"");
            } else {
                w.append(value);
            }
            firstValue = false;
        } else if (!children.isEmpty()) {
            for (i = 0; i < children.size(); i++) {
                children.get(i).toXSV(w, separator);
            }
        }
    }

    static public void TSVpreamble(Writer w) throws AppError {
    }

    static public void TSVpostamble(Writer w) throws AppError {
    }

    static public void CSVpreamble(Writer w) throws AppError {
    }

    static public void CSVpostamble(Writer w) throws AppError {
    }

    /**
     * Convert this information object into a JSON structure on the output
     * writer.
     *
     * This is a horribly complex function because of the differences between
     * XML and JSON. XML attributes are converted to JSON properties and appear
     * before any subelements. JSON cannot handle repeating subproperties, so
     * where these occur, one JSON property is output and the repeating
     * subproperties are produced as an array. The detection of repeating
     * subproperties is handled when children are added (addChild() above).
     *
     * @param w the writer to output the information object
     * @throws java.io.IOException if the writer fails
     * @throws VERSCommon.AppError
     */
    public void toJSON(Writer w) throws IOException, AppError {
        w.write("{\n");
        toJSON(w, 0);
        w.write("}");
    }

    private void toJSON(Writer w, int depth) throws IOException, AppError {
        int i, j;
        Information node;
        
        // if this information object has already been output (because it was
        // a repeating property, don't output it a second time
        if (output) {
            return;
        }

        // indent
        for (i = 0; i < depth; i++) {
            w.append(' ');
        }

        // output the final XML element name as the JSON property name
        if (elemPath != null) {
            w.append("\"" + elemPath.substring(elemPath.lastIndexOf('/') + 1) + "\"");
        } else {
            throw new AppError("Failed when creating XML - information object '" + elemPath + "' didn't contain a '/'");
        }
        w.append(": ");

        // output the JSON value; there are three cases: a simple value; where
        // there are repeating subelements (to be turned into a JSON array); and
        // where there are just multiple subelements
        // note that we do not (yet) handle the case where a simple value has
        // attributes
        if (value != null) { // simple value
            w.append("\"" + value + "\"");
        } else if (sameTag != null) { // repeating subelement to become an array
            node = this;
            w.append("[\n");

            // go through list of siblings that share the same tag name
            while (node != null) {
                node.output = true;
                for (j = 0; j < depth + 1; j++) {
                    w.append(" ");
                }

                // each sibling becomes an element in the array
                writeJSONprop(node, w, depth);

                // move to next sibling
                node = node.sameTag;
                if (node != null) {
                    w.append(",\n");
                }
            }
            w.append("]");
        } else if (!children.isEmpty()) { // simple list of subelements
            writeJSONprop(this, w, depth);
        } else {
            w.append("null");
        }
    }

    /**
     * Output an information object as a JSON property. Any attributes are
     * output as JSON properties, followed by any subproperties.
     * @param node the information object to be output
     * @param w the writer
     * @param depth the indent depth
     * @throws IOException if the write failed for any reason
     * @throws AppError if outputing the VEO failed
     */
    private void writeJSONprop(Information node, Writer w, int depth) throws IOException, AppError {
        int i, j;
        String[] s;
        boolean first;
        
        // each sibling becomes an element in the array
        w.append("{");
        first = true;

        // output any attributes associated with the sibling
        if (node.attributes != null) {
            for (i = 0; i < node.attributes.length; i++) {
                if (first) {
                    w.append("\n");
                    first = false;
                } else {
                    w.append(",\n");
                }
                for (j = 0; j < depth + 1; j++) {
                    w.append(" ");
                }
                s = node.attributes[i].split("=");
                w.append("\"");
                w.append(s[0]);
                w.append("\": ");
                w.append(s[1]);
            }
        }

        // output any subordinate children
        for (i = 0; i < node.children.size(); i++) {
            if (node.children.get(i).output) {
                continue;
            }
            if (first) {
                w.append("\n");
                first = false;
            } else {
                w.append(",\n");
            }
            node.children.get(i).toJSON(w, depth + 1);
        }
        w.append("}");
    }

    static public void JSONpreamble(Writer w) throws AppError {
        try {
            w.append("{\"report\":[");
        } catch (IOException ioe) {
            throw new AppError("Failed writing JSON output");
        }
    }

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
        toXML(w, 0);
    }

    public void toXML(Writer w, int depth) throws IOException, AppError {
        int i;
        String tag;

        if (elemPath != null) {
            tag = elemPath.substring(elemPath.lastIndexOf('/') + 1);
        } else {
            throw new AppError("Failed when creating XML - information object '" + elemPath + "' didn't contain a '/'");
        }

        // indent
        for (i = 0; i < depth; i++) {
            w.append(' ');
        }

        // start tag
        w.append("<");
        w.append(tag);

        // include attributes (if any)
        if (attributes != null) {
            for (i = 0; i < attributes.length; i++) {
                w.append(" ");
                w.append(attributes[i]);
            }
        }

        // include simple value if present
        if (value != null) {
            w.append(">");
            w.append(xmlEncode(value));
            w.append("</");
            w.append(tag);
            w.append(">\n");

            // otherwise include subelements if present    
        } else if (!children.isEmpty()) {
            w.append(">\n");
            for (i = 0; i < children.size(); i++) {
                children.get(i).toXML(w, depth + 1);
            }
            for (i = 0; i < depth; i++) {
                w.append(' ');
            }
            w.append("</");
            w.append(tag);
            w.append(">\n");

            // otherwise it is an empty element    
        } else {
            w.append("/>\n");
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
