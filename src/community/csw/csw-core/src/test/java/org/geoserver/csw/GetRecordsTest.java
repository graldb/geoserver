package org.geoserver.csw;

import static org.custommonkey.xmlunit.XMLAssert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.xml.namespace.QName;

import junit.framework.Test;
import net.opengis.cat.csw20.ElementSetNameType;
import net.opengis.cat.csw20.ElementSetType;
import net.opengis.cat.csw20.GetRecordsType;
import net.opengis.cat.csw20.QueryType;
import net.opengis.cat.csw20.ResultType;

import org.apache.commons.io.FileUtils;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.custommonkey.xmlunit.exceptions.XpathException;
import org.geoserver.csw.kvp.GetRecordsKvpRequestReader;
import org.geoserver.csw.xml.v2_0_2.CSWXmlReader;
import org.geoserver.data.test.MockData;
import org.geoserver.platform.ServiceException;
import org.geotools.csw.CSWConfiguration;
import org.geotools.csw.DC;
import org.geotools.xml.XmlConverterFactory;
import org.opengis.filter.Filter;
import org.opengis.filter.Not;
import org.opengis.filter.PropertyIsEqualTo;
import org.opengis.filter.expression.PropertyName;
import org.w3c.dom.Document;

public class GetRecordsTest extends CSWTestSupport {

    /**
     * This is a READ ONLY TEST so we can use one time setup
     */
    public static Test suite() {
        return new OneTimeTestSetup(new GetRecordsTest());
    }
    
    @Override
    protected void populateDataDirectory(MockData dataDirectory) throws Exception {
        super.populateDataDirectory(dataDirectory);
        
        // copy all records into the data directory
        File root = dataDirectory.getDataDirectoryRoot();
        File catalog = new File(root, "catalog");
        File records = new File("./src/test/resources/org/geoserver/csw/records");
        FileUtils.copyDirectory(records, catalog);
    }

    public void testKVPParameterCQL() throws Exception {
        Map<String, Object> raw = new HashMap<String, Object>();
        raw.put("service", "CSW");
        raw.put("version", "2.0.2");
        raw.put("request", "GetRecords");
        raw.put("namespace",
                "xmlns(csw=http://www.opengis.net/cat/csw/2.0.2),xmlns(rim=urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0)");
        raw.put("resultType", "results");
        raw.put("requestId", "myId");
        raw.put("outputFormat", "application/xml");
        raw.put("outputSchema", "http://www.opengis.net/cat/csw/2.0.2");
        raw.put("startPosition", "5");
        raw.put("maxRecords", "20");
        raw.put("typenames", "csw:Record,rim:RegistryPackage");

        raw.put("elementName", "dc:title,dct:abstract");
        raw.put("constraintLanguage", "CQL_TEXT");
        raw.put("constraint", "AnyText like '%pollution%'");
        raw.put("sortby", "title:A,abstract:D");
        raw.put("distributedSearch", "true");
        raw.put("hopCount", "10");
        raw.put("responsehandler", "http://www.geoserver.org");
        GetRecordsKvpRequestReader reader = new GetRecordsKvpRequestReader();
        reader.setApplicationContext(applicationContext);
        Object request = reader.createRequest();
        GetRecordsType gr = (GetRecordsType) reader.read(request, parseKvp(raw), raw);

        // basic checks
        assertEquals("CSW", gr.getService());
        assertEquals("2.0.2", gr.getVersion());
        assertEquals(ResultType.RESULTS, gr.getResultType());
        assertEquals("myId", gr.getRequestId());
        assertEquals("application/xml", gr.getOutputFormat());
        assertEquals("http://www.opengis.net/cat/csw/2.0.2", gr.getOutputSchema());
        assertNotNull(gr.getDistributedSearch());
        assertEquals(new Integer(10), gr.getDistributedSearch().getHopCount());
        assertEquals("http://www.geoserver.org", gr.getResponseHandler());

        // now onto the query
        QueryType query = (QueryType) gr.getQuery();
        assertEquals("AnyText like '%pollution%'", query.getConstraint().getCqlText());
        assertEquals(2, query.getTypeNames().size());
        assertEquals(new QName("http://www.opengis.net/cat/csw/2.0.2", "Record"), query
                .getTypeNames().get(0));
        assertEquals(new QName("urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0", "RegistryPackage"),
                query.getTypeNames().get(1));
        assertEquals(2, query.getElementName().size());
        assertEquals(2, query.getElementName().size());
    }

    public void testKVPParameterFilter() throws Exception {
        Map<String, Object> raw = new HashMap<String, Object>();
        raw.put("service", "CSW");
        raw.put("version", "2.0.2");
        raw.put("request", "GetRecords");
        raw.put("namespace", "xmlns(csw=http://www.opengis.net/cat/csw/2.0.2)");
        raw.put("typenames", "csw:Record");
        raw.put("elementSetName", "brief");
        raw.put("constraintLanguage", "FILTER");
        raw.put("constraint",
                "<ogc:Filter xmlns:ogc=\"http://www.opengis.net/ogc\"><ogc:Not><ogc:PropertyIsEqualTo><ogc:PropertyName>dc:title</ogc:PropertyName><ogc:Literal>foo</ogc:Literal></ogc:PropertyIsEqualTo></ogc:Not></ogc:Filter>");

        GetRecordsKvpRequestReader reader = new GetRecordsKvpRequestReader();
        reader.setApplicationContext(applicationContext);
        Object request = reader.createRequest();
        GetRecordsType gr = (GetRecordsType) reader.read(request, parseKvp(raw), raw);

        // basic checks
        assertEquals("CSW", gr.getService());
        assertEquals("2.0.2", gr.getVersion());

        // now onto the query
        QueryType query = (QueryType) gr.getQuery();
        
        // checking the filter is structured as expected, with the proper namespace support
        Filter filter = query.getConstraint().getFilter();
        assertTrue(filter instanceof Not);
        Filter negated = ((Not) filter).getFilter();
        assertTrue(negated instanceof PropertyIsEqualTo);
        PropertyName pname = (PropertyName) ((PropertyIsEqualTo) negated).getExpression1();
        assertEquals("dc:title/dc:value", pname.getPropertyName());
        assertNotNull(pname.getNamespaceContext());
        assertEquals(DC.NAMESPACE, pname.getNamespaceContext().getURI("dc"));
        
        
        assertEquals("1.1.0", query.getConstraint().getVersion());
        assertEquals(1, query.getTypeNames().size());
        assertEquals(new QName("http://www.opengis.net/cat/csw/2.0.2", "Record"), query
                .getTypeNames().get(0));
        assertEquals(ElementSetType.BRIEF, query.getElementSetName().getValue());
    }

    public void testXMLReaderParameter() throws Exception {
        CSWXmlReader reader = new CSWXmlReader("GetRecords", "2.0.2", new CSWConfiguration());
        GetRecordsType gr = (GetRecordsType) reader.read(null,
                getResourceAsReader("GetRecordsBrief.xml"), (Map) null);
        // check the attributes
        assertEquals("application/xml", gr.getOutputFormat());
        assertEquals("urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0", gr.getOutputSchema());

        // the query
        QueryType query = (QueryType) gr.getQuery();
        List<QName> expected = new ArrayList<QName>();
        String rimNamespace = "urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0";
        expected.add(new QName(rimNamespace, "Service"));
        expected.add(new QName(rimNamespace, "Classification"));
        expected.add(new QName(rimNamespace, "Association"));
        assertEquals(expected, query.getTypeNames());

        // the element set name
        ElementSetNameType esn = query.getElementSetName();
        expected.clear();
        expected.add(new QName(rimNamespace, "Service"));
        assertEquals(expected, esn.getTypeNames());
        assertEquals(ElementSetType.BRIEF, esn.getValue());
    }

    /*
     * Rigth now we don't support the "validate" mode, we need a way to re-encode the request in XML
     * or to snatch it from the raw POST request
     * 
     * public void testValidateRequest() throws Exception { String request =
     * "csw?service=CSW&version=2.0.2&request=GetRecords&typeNames=csw:Record&resultType=validate";
     * Document d = getAsDOM(request); }
     */

    public void testHitRequest() throws Exception {
        String request = "csw?service=CSW&version=2.0.2&request=GetRecords&typeNames=csw:Record";
        Document d = getAsDOM(request);
        // print(d);

        // we have the right kind of document
        assertXpathEvaluatesTo("1", "count(/csw:GetRecordsResponse)", d);
        XpathEngine xpath = XMLUnit.newXpathEngine();

        // check we have a timestamp that is a valid XML date, and it's GMT (we don't 
        // test parts of the date since we are bound to fail even the year if the test is run
        // across midnight of 
        String timestampPath = "/csw:GetRecordsResponse/csw:GetSearchStatus/@timestamp";
        String timeStamp = xpath.evaluate(timestampPath, d);
        assertNotNull(timeStamp);
        Calendar cal = new XmlConverterFactory()
                .createConverter(String.class, Calendar.class, null).convert(timeStamp,
                        Calendar.class);
        assertNotNull(cal);
        assertEquals(TimeZone.getTimeZone("GMT"), cal.getTimeZone());
        
        // check we have the expected results
        assertXpathEvaluatesTo("summary", "//csw:SearchResults/@elementSet", d);
        assertXpathEvaluatesTo("12", "//csw:SearchResults/@numberOfRecordsMatched", d);
        assertXpathEvaluatesTo("10", "//csw:SearchResults/@numberOfRecordsReturned", d);
        assertXpathEvaluatesTo("11", "//csw:SearchResults/@nextRecord", d);
        
        // check we have no results
        assertXpathEvaluatesTo("0", "count(//csw:SearchResults/*)", d);
    }
    
    public void testHitMaxOffset() throws Exception {
        String request = "csw?service=CSW&version=2.0.2&request=GetRecords&typeNames=csw:Record&startPosition=5&maxRecords=2";
        Document d = getAsDOM(request);
        // print(d);

        // we have the right kind of document
        assertXpathEvaluatesTo("1", "count(/csw:GetRecordsResponse)", d);
        XpathEngine xpath = XMLUnit.newXpathEngine();

        // check we have the expected results
        assertXpathEvaluatesTo("summary", "//csw:SearchResults/@elementSet", d);
        assertXpathEvaluatesTo("12", "//csw:SearchResults/@numberOfRecordsMatched", d);
        assertXpathEvaluatesTo("2", "//csw:SearchResults/@numberOfRecordsReturned", d);
        assertXpathEvaluatesTo("7", "//csw:SearchResults/@nextRecord", d);
        
        // check we have no results
        assertXpathEvaluatesTo("0", "count(//csw:SearchResults/*)", d);
    }
    
    public void testInvalidStartPosition() throws Exception {
        String request = "csw?service=CSW&version=2.0.2&request=GetRecords&typeNames=csw:Record&startPosition=0";
        Document d = getAsDOM(request);
        // print(d);
        checkOws10Exception(d, ServiceException.INVALID_PARAMETER_VALUE, "startPosition");
    }
    
    public void testInvalidOutputSchema() throws Exception {
        String request = "csw?service=CSW&version=2.0.2&request=GetRecords&typeNames=csw:Record&outputSchema=http://www.geoserver.org";
        Document d = getAsDOM(request);
        // print(d);
        checkOws10Exception(d, ServiceException.INVALID_PARAMETER_VALUE, "outputSchema");
    }
    
    public void testAllRecordsDefaultElementSet() throws Exception {
        String request = "csw?service=CSW&version=2.0.2&request=GetRecords&typeNames=csw:Record&resultType=results";
        Document d = getAsDOM(request);
        
        // check we have the expected results
        assertXpathEvaluatesTo("summary", "//csw:SearchResults/@elementSet", d);
        assertXpathEvaluatesTo("12", "//csw:SearchResults/@numberOfRecordsMatched", d);
        assertXpathEvaluatesTo("10", "//csw:SearchResults/@numberOfRecordsReturned", d);
        assertXpathEvaluatesTo("11", "//csw:SearchResults/@nextRecord", d);
        
        // check we 10 summary records (max records defaults to 10)
        assertXpathEvaluatesTo("10", "count(//csw:SearchResults/csw:SummaryRecord)", d);
    }
    
    public void testAllRecordsBrief() throws Exception {
        String request = "csw?service=CSW&version=2.0.2&request=GetRecords&typeNames=csw:Record&resultType=results&elementSetName=brief";
        Document d = getAsDOM(request);
        
        // check we have the expected results
        assertXpathEvaluatesTo("brief", "//csw:SearchResults/@elementSet", d);
        assertXpathEvaluatesTo("12", "//csw:SearchResults/@numberOfRecordsMatched", d);
        assertXpathEvaluatesTo("10", "//csw:SearchResults/@numberOfRecordsReturned", d);
        assertXpathEvaluatesTo("11", "//csw:SearchResults/@nextRecord", d);
        
        // check we 10 summary records (max records defaults to 10)
        assertXpathEvaluatesTo("10", "count(//csw:SearchResults/csw:BriefRecord)", d);
    }
    
    public void testAllRecordsFull() throws Exception {
        String request = "csw?service=CSW&version=2.0.2&request=GetRecords&typeNames=csw:Record&resultType=results&elementSetName=full";
        Document d = getAsDOM(request);
        
        // check we have the expected results
        assertXpathEvaluatesTo("full", "//csw:SearchResults/@elementSet", d);
        assertXpathEvaluatesTo("12", "//csw:SearchResults/@numberOfRecordsMatched", d);
        assertXpathEvaluatesTo("10", "//csw:SearchResults/@numberOfRecordsReturned", d);
        assertXpathEvaluatesTo("11", "//csw:SearchResults/@nextRecord", d);
        
        // check we 10 summary records (max records defaults to 10)
        assertXpathEvaluatesTo("10", "count(//csw:SearchResults/csw:Record)", d);
    }
    
    public void testEmptyResult() throws Exception {
        String request = "csw?service=CSW&version=2.0.2&request=GetRecords&typeNames=csw:Record&resultType=results&constraint=dc:title = 'foo'";
        Document d = getAsDOM(request);

        // print(d);
        assertXpathEvaluatesTo("summary", "//csw:SearchResults/@elementSet", d);
        assertXpathEvaluatesTo("0", "//csw:SearchResults/@numberOfRecordsMatched", d);
        assertXpathEvaluatesTo("0", "//csw:SearchResults/@numberOfRecordsReturned", d);
        assertXpathEvaluatesTo("0", "//csw:SearchResults/@nextRecord", d);
    }

    
    public void testTitleFilter() throws Exception {
        String request = "csw?service=CSW&version=2.0.2&request=GetRecords&typeNames=csw:Record&resultType=results&elementSetName=brief&constraint=dc:title like '%25ipsum%25'";
        Document d = getAsDOM(request);
        // print(d);

        assertIpsumRecords(d);
    }
    
    public void testUnqualifiedTitleFilter() throws Exception {
        String request = "csw?service=CSW&version=2.0.2&request=GetRecords&typeNames=csw:Record&resultType=results&elementSetName=brief&constraint=title like '%25ipsum%25'";
        Document d = getAsDOM(request);
        // print(d);

        assertIpsumRecords(d);
    }
    
    private void assertIpsumRecords(Document d) throws XpathException {
        // basic checks
        assertXpathEvaluatesTo("brief", "//csw:SearchResults/@elementSet", d);
        assertXpathEvaluatesTo("2", "//csw:SearchResults/@numberOfRecordsMatched", d);
        assertXpathEvaluatesTo("2", "//csw:SearchResults/@numberOfRecordsReturned", d);
        assertXpathEvaluatesTo("0", "//csw:SearchResults/@nextRecord", d);
        assertXpathEvaluatesTo("2", "count(//csw:SearchResults/*)", d);
        
        // verify we got the records we expected
        assertXpathEvaluatesTo("1", "count(//csw:BriefRecord[dc:identifier='urn:uuid:19887a8a-f6b0-4a63-ae56-7fba0e17801f'])", d);
        assertXpathEvaluatesTo("1", "count(//csw:BriefRecord[dc:identifier='urn:uuid:a06af396-3105-442d-8b40-22b57a90d2f2'])", d);
    }
    
    public void testFullTextSearch() throws Exception {
        String request = "csw?service=CSW&version=2.0.2&request=GetRecords&typeNames=csw:Record&resultType=results&elementSetName=brief&constraint=AnyText like '%25sed%25'";
        Document d = getAsDOM(request);
        // print(d);

        // basic checks
        assertXpathEvaluatesTo("brief", "//csw:SearchResults/@elementSet", d);
        assertXpathEvaluatesTo("3", "//csw:SearchResults/@numberOfRecordsMatched", d);
        assertXpathEvaluatesTo("3", "//csw:SearchResults/@numberOfRecordsReturned", d);
        assertXpathEvaluatesTo("0", "//csw:SearchResults/@nextRecord", d);
        assertXpathEvaluatesTo("3", "count(//csw:SearchResults/*)", d);
        
        // verify we got the records we expected
        // this one has 'sed' in the abstract
        assertXpathEvaluatesTo("1", "count(//csw:BriefRecord[dc:identifier='urn:uuid:19887a8a-f6b0-4a63-ae56-7fba0e17801f'])", d);
        // this one in the abstract
        assertXpathEvaluatesTo("1", "count(//csw:BriefRecord[dc:identifier='urn:uuid:66ae76b7-54ba-489b-a582-0f0633d96493'])", d);
        // and this one in the title
        assertXpathEvaluatesTo("1", "count(//csw:BriefRecord[dc:identifier='urn:uuid:94bc9c83-97f6-4b40-9eb8-a8e8787a5c63'])", d);
    }

}