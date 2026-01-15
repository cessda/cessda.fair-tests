package eu.cessda.fairtests.server;

import eu.cessda.fairtests.FairTests;
import eu.cessda.fairtests.Result;
import eu.cessda.fairtests.TestType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api")
public class FairTestsController {
    private final FairTests fairTests;

    public FairTestsController(FairTests fairTests) {
        this.fairTests = fairTests;
    }

    @GetMapping(path = "{test}")
    public Result accessRights(@PathVariable TestType test, String url) {
        return fairTests.runTest(test, url);
    }
}
