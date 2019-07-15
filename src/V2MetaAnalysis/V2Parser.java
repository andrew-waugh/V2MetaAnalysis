/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package V2MetaAnalysis;

import VERSCommon.AppError;
import VERSCommon.AppFatal;
import VERSCommon.HandleElement;
import VERSCommon.XMLConsumer;
import VERSCommon.XMLParser;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This class parses V2 VEOs looking for elements that are of interest. It
 * copies those values into information structures.
 *
 * @author Andrew
 */
public class V2Parser implements XMLConsumer {

    XMLParser xmlp;             // the XML parser
    Target targets;  // elements of interest from VEOs
    private final static Logger LOG = Logger.getLogger("V2MetaAnalysis.V2MetaAnalysis");

    /**
     * Construct a new V2 VEO parser
     *
     * @param targets list of elements to be harvested
     * @throws AppFatal if a permanent error occurred
     */
    public V2Parser(Target targets) throws AppFatal {
        xmlp = new XMLParser(this);
        this.targets = targets;
    }

    /**
     * Parse a VEO file, building a collection of information from it
     *
     * @param veoFile the VEO file to harvest
     * @throws VERSCommon.AppFatal if a fatal error occurred (no sense in going
     * on)
     * @throws VERSCommon.AppError if a VEO error occurred (can repeat with new
     * VEO)
     */
    public void parse(Path veoFile) throws AppFatal, AppError {
        xmlp.parse(veoFile);
    }

    /**
     * The XML parser has found the start of an element. Check to see if this
     * element is of interest, if so, remember that we want the values
     *
     * @param elementPath
     * @param attributes
     * @return
     * @throws SAXException
     */
    @Override
    public HandleElement startElement(String elementPath, Attributes attributes) throws SAXException {
        int i;
        HandleElement he;

        he = null;
        for (i = 0; i < targets.size(); i++) {
            // System.out.println("Look for "+targets.get(i));
            // System.out.println("Given    "+elementPath);
            if (targets.get(i).matchElemPath(elementPath)) {
                he = new HandleElement(HandleElement.VALUE_TO_STRING);
                targets.get(i).addAttributes(attributes);
                // System.out.println("Harvest! " + elementPath);
            }
        }
        return he;
    }

    /**
     * The XML parser has found the end of an element - the start should be on
     * the top of the Stack.
     *
     * @param elementPath
     * @param value
     * @param element
     * @throws SAXException
     */
    @Override
    public void endElement(String elementPath, String value, String element) throws SAXException {
        int i;
        String s;

        // remember the value harvested (null if none)
        for (i = 0; i < targets.size(); i++) {
            if (targets.get(i).matchElemPath(elementPath)) {
                if (value != null) {
                    LOG.log(Level.FINE, "Harvesting {0} ''{1}''", new Object[]{elementPath, value});
                } else {
                    LOG.log(Level.FINE, "Harvesting {0} <Null>", elementPath);
                }
                if (value != null) {
                    targets.get(i).value.add(value);
                }
            }
        }
    }
}
