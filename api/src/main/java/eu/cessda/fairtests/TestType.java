package eu.cessda.fairtests;

// Enums
public enum TestType {
    ACCESS_RIGHTS("access-rights"),
    PID("pid"),
    ELSST_KEYWORDS("elsst-keywords"),
    DDI_VOCABS("ddi-vocabs"),
    DDI_SAMPLEPROC("ddi-sampleproc"),
    TOPIC_CLASS("topic-class");

    private final String name;

    TestType(String name) {
        this.name = name;
    }

    public String testName() {
        return name;
    }
}
