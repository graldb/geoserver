package org.geoserver.csw;

import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Test;
import net.opengis.ows10.GetCapabilitiesType;

import org.custommonkey.xmlunit.NamespaceContext;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.eclipse.emf.common.util.EList;
import org.geoserver.csw.kvp.GetCapabilitiesKvpRequestReader;
import org.geoserver.csw.xml.v2_0_2.CSWXmlReader;
import org.geoserver.ows.xml.v1_0.OWS;
import org.geoserver.platform.ServiceException;
import org.geotools.csw.CSWConfiguration;
import org.geotools.filter.v1_1.OGC;
import org.geotools.xlink.XLINK;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXParseException;

public class GetCapabilitiesTest extends CSWTestSupport {

    static XpathEngine xpath = XMLUnit.newXpathEngine();

    static {
        Map<String, String> prefixMap = new HashMap<String, String>();
        prefixMap.put("ows", OWS.NAMESPACE);
        prefixMap.put("ogc", OGC.NAMESPACE);
        prefixMap.put("gml", "http://www.opengis.net/gml");
        prefixMap.put("gmd", "http://www.isotc211.org/2005/gmd");
        prefixMap.put("xlink", XLINK.NAMESPACE);
        NamespaceContext nameSpaceContext = new SimpleNamespaceContext(prefixMap);
        xpath.setNamespaceContext(nameSpaceContext);
    }

    /**
     * This is a READ ONLY TEST so we can use one time setup
     */
    public static Test suite() {
        return new OneTimeTestSetup(new GetCapabilitiesTest());
    }

    public void testKVPReader() throws Exception {
        Map<String, Object> raw = new HashMap<String, Object>();
        raw.put("service", "CSW");
        raw.put("request", "GetCapabilities");
        raw.put("acceptVersions", "2.0.2,2.0.0,0.7.2");
        raw.put("sections", "OperationsMetadata,foo");
        raw.put("acceptFormats", "application/xml,text/plain");

        GetCapabilitiesKvpRequestReader reader = new GetCapabilitiesKvpRequestReader();
        Object request = reader.createRequest();
        GetCapabilitiesType caps = (GetCapabilitiesType) reader.read(request, parseKvp(raw), raw);
        assertReturnedCapabilitiesComplete(caps);
    }

    private void assertReturnedCapabilitiesComplete(GetCapabilitiesType caps) {
        assertNotNull(caps);

        EList versions = caps.getAcceptVersions().getVersion();
        assertEquals(3, versions.size());
        assertEquals("2.0.2", versions.get(0));
        assertEquals("2.0.0", versions.get(1));
        assertEquals("0.7.2", versions.get(2));

        EList sections = caps.getSections().getSection();
        assertEquals(2, sections.size());
        assertEquals("OperationsMetadata", sections.get(0));
        assertEquals("foo", sections.get(1));

        EList outputFormats = caps.getAcceptFormats().getOutputFormat();
        assertEquals(2, outputFormats.size());
        assertEquals("application/xml", outputFormats.get(0));
        assertEquals("text/plain", outputFormats.get(1));
    }

    public void testXMLReader() throws Exception {
        CSWXmlReader reader = new CSWXmlReader("GetCapabilities", "2.0.2", new CSWConfiguration());
        GetCapabilitiesType caps = (GetCapabilitiesType) reader.read(null,
                getResourceAsReader("GetCapabilities.xml"), (Map) null);
        assertReturnedCapabilitiesComplete(caps);
    }

    public void testXMLReaderInvalid() throws Exception {
        // create a schema invalid request
        String capRequest = getResourceAsString("GetCapabilities.xml");
        capRequest = capRequest.replace("ows:Sections", "ows:foo");
        try {
            CSWXmlReader reader = new CSWXmlReader("GetCapabilities", "2.0.2",
                    new CSWConfiguration());
            reader.read(null, new StringReader(capRequest), (Map) null);
            fail("the parsing should have failed, the document is invalid");
        } catch (ServiceException e) {
            // it is a validation exception right?
            assertTrue(e.getCause() instanceof SAXParseException);
            SAXParseException cause = (SAXParseException) e.getCause();
            assertTrue(cause.getMessage().contains("ows:foo"));
        }
    }

    public void testGetBasic() throws Exception {
        Document dom = getAsDOM(BASEPATH + "?service=csw&version=2.0.2&request=GetCapabilities");
        // print(dom);
        checkValidationErrors(dom);

        // basic check on local name
        Element e = dom.getDocumentElement();
        assertEquals("Capabilities", e.getLocalName());

        // basic check on xpath node
        assertXpathEvaluatesTo("1", "count(/csw:Capabilities)", dom);

        assertTrue(xpath.getMatchingNodes("//ows:OperationsMetadata/ows:Operation", dom)
                .getLength() > 0);
        assertEquals("5", xpath.evaluate("count(//ows:Operation)", dom));

        // basic check on GetCapabilities operation constraint
        assertEquals(
                "XML",
                xpath.evaluate(
                        "//ows:OperationsMetadata/ows:Operation[@name=\"GetCapabilities\"]/ows:Constraint/ows:Value",
                        dom));
    }

    public void testPostBasic() throws Exception {
        Document dom = postAsDOM(BASEPATH + "?service=csw&version=2.0.2&request=GetCapabilities");
        // print(dom);
        checkValidationErrors(dom);

        // basic check on local name
        Element e = dom.getDocumentElement();
        assertEquals("Capabilities", e.getLocalName());

        // basic check on xpath node
        assertXpathEvaluatesTo("1", "count(/csw:Capabilities)", dom);

        assertTrue(xpath.getMatchingNodes("//ows:OperationsMetadata/ows:Operation", dom)
                .getLength() > 0);
        assertEquals("5", xpath.evaluate("count(//ows:Operation)", dom));

        // basic check on GetCapabilities operation constraint
        assertEquals(
                "XML",
                xpath.evaluate(
                        "//ows:OperationsMetadata/ows:Operation[@name=\"GetCapabilities\"]/ows:Constraint/ows:Value",
                        dom));
    }

    public void testSections() throws Exception {
        Document dom = getAsDOM(BASEPATH + "?service=csw&version=2.0.2&request=GetCapabilities&sections=ServiceIdentification,ServiceProvider");
        // print(dom);
        //checkValidationErrors(dom);

        // basic check on local name
        Element e = dom.getDocumentElement();
        assertEquals("Capabilities", e.getLocalName());

        // basic check on xpath node
        assertXpathEvaluatesTo("1", "count(/csw:Capabilities)", dom);
        assertEquals("1", xpath.evaluate("count(//ows:ServiceIdentification)", dom));
        assertEquals("1", xpath.evaluate("count(//ows:ServiceProvider)", dom));
        assertEquals("0", xpath.evaluate("count(//ows:OperationsMetadata)", dom));
        assertEquals("0", xpath.evaluate("count(//ogc:Filter_Capabilities)", dom));

        assertTrue(xpath.getMatchingNodes("//ows:OperationsMetadata/ows:Operation", dom).getLength() == 0);
        assertEquals("0", xpath.evaluate("count(//ows:Operation)", dom));
    }
}