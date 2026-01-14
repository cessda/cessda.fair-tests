package eu.cessda.fairtests.server;

import eu.cessda.fairtests.TestType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StringToTestTypeConverter implements Converter<String, TestType> {
    private final Map<String, TestType> testMap;

    public StringToTestTypeConverter() {
        this.testMap = new ConcurrentHashMap<>();
        for (TestType testType : EnumSet.allOf(TestType.class)) {
            testMap.put(testType.testName(), testType);
        }
    }

    @Override
    public TestType convert(String source) {
        return testMap.get(source);
    }
}
