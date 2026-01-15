package eu.cessda.fairtests.server;

import eu.cessda.fairtests.FairTests;
import eu.cessda.fairtests.Result;
import eu.cessda.fairtests.TestType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api")
public class FairTestsController {
    private final FairTests fairTests;

    public FairTestsController(FairTests fairTests) {
        this.fairTests = fairTests;
    }

    @GetMapping(path = "{test}")
    public Result accessRights(@PathVariable(name = "test") TestType test, @RequestParam(name = "url") String url) {
        return fairTests.runTest(test, url);
    }
}
