package com.homework2.support.importer;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlImportParserTest {
    private final XmlTicketImportParser parser = new XmlTicketImportParser();

    @Test
    void supportsXmlExtensionAndContentType() {
        assertThat(parser.supports("tickets.xml", null)).isTrue();
        assertThat(parser.supports("tickets.txt", "application/xml")).isTrue();
        assertThat(parser.supports("tickets.txt", "application/json")).isFalse();
    }

    @Test
    void parseXmlTicketsReturnsRecords() {
        String xml = """
                <tickets>
                  <ticket>
                    <customer_id>cust-1</customer_id>
                    <customer_email>good@example.com</customer_email>
                    <customer_name>Good User</customer_name>
                    <subject>Login issue</subject>
                    <description>Cannot access account because password reset fails</description>
                    <category>account_access</category>
                    <priority>high</priority>
                    <status>new</status>
                    <tags>
                      <tag>login</tag>
                      <tag>password</tag>
                    </tags>
                    <metadata>
                      <source>web_form</source>
                      <browser>Chrome</browser>
                      <device_type>desktop</device_type>
                    </metadata>
                  </ticket>
                </tickets>
                """;

        List<ImportRecord> records = parser.parse(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().hasParseError()).isFalse();
        assertThat(records.getFirst().ticketRequest().metadata().browser()).isEqualTo("Chrome");
    }

    @Test
    void malformedXmlThrowsMalformedImportFileException() {
        String malformedXml = "<tickets><ticket></tickets>";

        assertThatThrownBy(() -> parser.parse(malformedXml.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(MalformedImportFileException.class)
                .hasMessageContaining("Malformed XML file");
    }

    @Test
    void parseSampleXmlFileReturnsExpectedRecords() throws Exception {
        byte[] xml = Files.readAllBytes(Path.of("sample_tickets.xml"));

        List<ImportRecord> records = parser.parse(xml);

        assertThat(records).hasSize(30);
        assertThat(records).allMatch(record -> !record.hasParseError());
    }

    @Test
    void parseInvalidXmlFileContainsParseErrors() throws Exception {
        byte[] xml = Files.readAllBytes(Path.of("invalid_tickets.xml"));

        List<ImportRecord> records = parser.parse(xml);

        assertThat(records).hasSize(2);
        assertThat(records).anyMatch(ImportRecord::hasParseError);
    }
}
