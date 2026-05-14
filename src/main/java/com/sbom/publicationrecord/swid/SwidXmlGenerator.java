package com.sbom.publicationrecord.swid;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.springframework.stereotype.Component;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
public class SwidXmlGenerator {
    // defines a constant XML namespace URI for the ISO/IEC 19770-2:2015 SWID (Software Identification) tag XML schema
    static final String SWID_NS = "http://standards.iso.org/iso/19770/-2/2015/schema.xsd";
    static final String XML_NS = "http://www.w3.org/XML/1998/namespace";

    // Creates a meta XML element in the SWID namespace with name and value attributes
    private Element meta(Document doc, String name, String value){
        Element meta = doc.createElementNS(SWID_NS, "Meta");
        meta.setAttribute("name", name);
        meta.setAttribute("value", value);
        return meta;
    }
    // Builds SWID software identity XML document from tag and writes it into output file
    public Path writeTag(SwidTag tag, Path outputFile){
        try {
            Files.createDirectories(outputFile.getParent());

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().newDocument();

            Element root = document.createElementNS(SWID_NS, "SoftwareIdentity");
            root.setAttribute("name", tag.softwareName());
            root.setAttribute("version", tag.version());
            root.setAttribute("tagId", tag.tagId());
            root.setAttribute("tagVersion", String.valueOf(tag.tagVersion()));
            root.setAttribute("versionScheme", tag.versionScheme());
            root.setAttribute("corpus", Boolean.toString(tag.corpus()));
            root.setAttribute("patch", "false");
            root.setAttribute("supplemental", "false");
            root.setAttributeNS(XML_NS, "xml:lang", tag.xmlLang());
            document.appendChild(root);

            Element entity = document.createElementNS(SWID_NS, "Entity");
            entity.setAttribute("name", tag.entityName());
            entity.setAttribute("role", "tagCreator");
            entity.setAttribute("regid", tag.regId());
            root.appendChild(entity);

            Element sourceLink = document.createElementNS(SWID_NS, "Link");
            sourceLink.setAttribute("rel", "source");
            sourceLink.setAttribute("href", tag.gitUrl());
            root.appendChild(sourceLink);

            // Keep provenance as explicit extension-like Meta entries for research traceability.
            root.appendChild(meta(document, "sourceRepository", tag.gitUrl()));
            root.appendChild(meta(document, "commitSha", tag.commitSha()));
            root.appendChild(meta(document, "sbomFileName", tag.sbomFileName()));
            root.appendChild(meta(document, "sbomHashSha256", tag.sbomHashSha256()));
            root.appendChild(meta(document, "generatedAt", tag.generatedAt().toString()));

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            try (OutputStream out = Files.newOutputStream(
                    outputFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            )) {
                transformer.transform(new DOMSource(document), new StreamResult(out));
            }

            return outputFile;}
        catch (Exception e) {
            throw new IllegalStateException("Failed to generate SWID file: " + outputFile, e);
        }
    }

}
