package com.github.dropguard.summer.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

public class ProductionOnlyImportOption implements ImportOption {
    @Override
    public boolean includes(Location location) {
        String path = location.asURI().toString();
        return !path.contains("/summer-tck-fixtures/")
                && !path.contains("/summer-realworld/")
                && !path.contains("/summer-twitter/")
                && !path.contains("/summer-issue-tracker/")
                && !path.contains("/samples/")
                && !path.contains("/summer-benchmark/")
                && !path.contains("/summer-archunit-clean/"); // just in case
    }
}
