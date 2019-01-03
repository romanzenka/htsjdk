package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import htsjdk.variant.VariantBaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class VCFHeaderLineTranslatorUnitTest extends VariantBaseTest {

    @Test
    public void testParseVCF4HeaderLine() {
        // the following tests exercise the escaping of quotes and backslashes in VCF header lines

        // test a case with no escapes
        final Map<String, String> values = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2,
                                                                             "<ID=SnpCluster,Description=\"SNPs found in clusters\">",
                                                                             null);
        Assert.assertEquals(values.size(), 2);
        Assert.assertEquals(values.get("ID"), "SnpCluster");
        Assert.assertEquals(values.get("Description"), "SNPs found in clusters");

        // test escaped quotes
        final Map<String, String> values2 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2,
                                                                              "<ID=ANNOTATION,Description=\"ANNOTATION != \\\"NA\\\" || ANNOTATION <= 0.01\">",
                                                                              null);
        Assert.assertEquals(values2.size(), 2);
        Assert.assertEquals(values2.get("ID"), "ANNOTATION");
        Assert.assertEquals(values2.get("Description"), "ANNOTATION != \"NA\" || ANNOTATION <= 0.01");

        // test escaped quotes and an escaped backslash
        final Map<String, String> values3 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2,
                                                                              "<ID=ANNOTATION,Description=\"ANNOTATION \\\\= \\\"NA\\\" || ANNOTATION <= 0.01\">",
                                                                              null);
        Assert.assertEquals(values3.size(), 2);
        Assert.assertEquals(values3.get("ID"), "ANNOTATION");
        Assert.assertEquals(values3.get("Description"), "ANNOTATION \\= \"NA\" || ANNOTATION <= 0.01");

        // test a header line with two value tags, one with an escaped backslash and two escaped quotes, one with an escaped quote
        final Map<String, String> values4 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2,
                                                                              "<ID=ANNOTATION,Description=\"ANNOTATION \\\\= \\\"NA\\\" || ANNOTATION <= 0.01\", Description2=\"foo\\\"bar\">",
                                                                              null);
        Assert.assertEquals(values4.size(), 3);
        Assert.assertEquals(values4.get("ID"), "ANNOTATION");
        Assert.assertEquals(values4.get("Description"), "ANNOTATION \\= \"NA\" || ANNOTATION <= 0.01");
        Assert.assertEquals(values4.get("Description2"), "foo\"bar");

        // test a line with a backslash that appears before something other than a quote or backslash
        final Map<String, String> values5 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2,
                                                                              "<ID=ANNOTATION,Description=\"ANNOTATION \\n with a newline in it\">",
                                                                              null);
        Assert.assertEquals(values5.size(), 2);
        Assert.assertEquals(values5.get("ID"), "ANNOTATION");
        Assert.assertEquals(values5.get("Description"), "ANNOTATION \\n with a newline in it");

        // test with an unclosed quote
        try {
            final Map<String, String> values6 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2,
                                                                                  "<ID=ANNOTATION,Description=\"ANNOTATION \\n with a newline in it>",
                                                                                  null);
            Assert.fail("Should have thrown a TribbleException for having an unclosed quote in the description line");
        }
        catch (TribbleException.InvalidHeader e) {
        }

        // test with an escaped quote at the end
        try {
            final Map<String, String> values7 = VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_2,
                                                                                  "<ID=ANNOTATION,Description=\"ANNOTATION \\n with a newline in it\\\">",
                                                                                  null);
            Assert.fail("Should have thrown a TribbleException for having an unclosed quote in the description line");
        }
        catch (TribbleException.InvalidHeader e) {
        }

    }

    @DataProvider(name = "testData")
    private Object[][] getTestData() {
        String line = "<ID=X,Description=\"Y\">";
        return new Object[][]{
                {line, "ID,Description", "", ""},
                {line, "ID", "Description", ""},
                {line, "ID", "Description,Optional2", ""},
                {line, "", "Description,ID", ""},
                {line, "Description,ID", "", "Tag ID in wrong order"},
                {line, "Description", "ID", "Optional tag ID must be listed after all expected tags"},
                {line, "ID,Desc", "", "Unexpected tag Description"},
                {line, "ID", "Desc", "Unexpected tag Description"},
                {line, "ID,Description,Extra", "", ""},
                {"<>", "", "", ""},
                {"<>", "", "ID,Description", ""},
                {"<>", "ID", "", ""},
                {"<ID=X,Description=\"Y\",Extra=E>", "ID", "Description", "Unexpected tag count 3"},
                {"<ID=X,Description=<Y>>", "ID,Description", "", ""}
        };
    }

    @Test(dataProvider = "testData")
    public void testParseVCF4HeaderLineWithTags(final String line,
                                                final String expected,
                                                final String optional,
                                                final String error) {
        try {
            final List<String> expectedTagOrder = expected.isEmpty() ?
                    Collections.emptyList() :
                    Arrays.asList(expected.split(","));
            final List<String> optionalTags = optional.isEmpty() ?
                    Collections.emptyList() :
                    Arrays.asList(optional.split(","));

            if (optionalTags.isEmpty()) {
                VCFHeaderLineTranslator.parseLine(
                        VCFHeaderVersion.VCF4_2,
                        line,
                        expectedTagOrder
                );
            }
            else {
                VCFHeaderLineTranslator.parseLine(
                        VCFHeaderVersion.VCF4_2,
                        line,
                        expectedTagOrder,
                        optionalTags
                );
            }
            if (!error.isEmpty()) {
                Assert.fail("Expected failure: '" + error + "', got success");
            }
        }
        catch (Exception e) {
            if (error.isEmpty()) {
                Assert.fail("Expected success, got failure", e);
            }
            Assert.assertTrue(
                    e.getMessage().contains(error),
                    String.format("Error string '%s' should be present in error message '%s'", error, e.getMessage())
            );
        }
    }

    @DataProvider(name = "vcfv3")
    private Object[][] getVcfV3Versions() {
        return new Object[][]{
                {VCFHeaderVersion.VCF3_2},
                {VCFHeaderVersion.VCF3_3}
        };
    }

    @Test(dataProvider = "vcfv3", expectedExceptions = TribbleException.class)
    public void testVcfV3FailsOptionalTags(final VCFHeaderVersion vcfVersion) {
        VCFHeaderLineTranslator.parseLine(
                vcfVersion,
                "<ID=X,Description=\"Y\">",
                Arrays.asList("ID"),
                Arrays.asList("Description")
        );
    }
}
