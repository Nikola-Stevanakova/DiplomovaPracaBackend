package org.example.starter.startup

import com.netgrif.application.engine.startup.*

class CustomRunnerController extends RunnerController {

    private List order = [
            ElasticsearchRunner,
            MongoDbRunner,
            ProcessReaderRunner,
            StorageRunner,
            RuleEngineRunner,
            DefaultRoleRunner,
            AnonymousRoleRunner,
            AuthorityRunner,
            SystemUserRunner,
            FunctionsCacheRunner,
            FilterRunner,
            GroupRunner,
            DefaultFiltersRunner,
            SuperCreator,
            FlushSessionsRunner,
            MailRunner,
            PostalCodeImporter,
            // CUSTOM IMPORT RUNNERS
//TODO:     CUSTOM IMPORT RUNNER
            NetImportRunner,
            // END OF CUSTOM IMPORT RUNNERS
            DemoRunner,
            QuartzSchedulerRunner,
            PdfRunner,
            FinisherRunnerSuperCreator,
            // ADDITIONAL CUSTOM RUNNERS
//TODO:     CUSTOM RUNNERS
            CustomRunner,
            // END OF ADDITIONAL CUSTOM RUNNERS
            FinisherRunner,
    ]

    @Override
    protected List getOrderList() {
        return order
    }

}