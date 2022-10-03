package com.backbase.productled.service;

import static com.backbase.mambu.clients.model.DepositAccountAction.ActionEnum.APPROVE;
import static com.backbase.mambu.clients.model.DepositAccountAction.ActionEnum.CLOSE;
import static com.backbase.mambu.clients.model.DepositAccountAction.ActionEnum.CLOSE_REJECT;
import static com.backbase.mambu.clients.model.DepositAccountAction.ActionEnum.CLOSE_WITHDRAW;
import static com.backbase.mambu.clients.model.DepositAccountAction.ActionEnum.LOCK;
import static com.backbase.mambu.clients.model.ScheduleSettings.RepaymentPeriodUnitEnum.DAYS;
import static com.backbase.mambu.clients.model.ScheduleSettings.RepaymentPeriodUnitEnum.MONTHS;
import static com.backbase.mambu.clients.model.ScheduleSettings.RepaymentPeriodUnitEnum.WEEKS;
import static com.backbase.mambu.clients.model.ScheduleSettings.RepaymentPeriodUnitEnum.YEARS;
import static com.backbase.stream.LegalEntitySaga.CREATED;
import static com.backbase.stream.legalentity.model.LegalEntityType.BANK;
import static org.springframework.data.mapping.Alias.ofNullable;
import static org.springframework.util.CollectionUtils.isEmpty;

import com.backbase.mambu.clients.api.CentresConfigurationApi;
import com.backbase.mambu.clients.api.ClientsApi;
import com.backbase.mambu.clients.api.DepositAccountsApi;
import com.backbase.mambu.clients.api.DepositTransactionsApi;
import com.backbase.mambu.clients.api.DocumentsApi;
import com.backbase.mambu.clients.api.LoanAccountsApi;
import com.backbase.mambu.clients.api.LoanTransactionsApi;
import com.backbase.mambu.clients.model.Address;
import com.backbase.mambu.clients.model.AddressDetails;
import com.backbase.mambu.clients.model.Asset;
import com.backbase.mambu.clients.model.CentreConfiguration;
import com.backbase.mambu.clients.model.CentresConfiguration;
import com.backbase.mambu.clients.model.Client;
import com.backbase.mambu.clients.model.ClientCustomFieldsClients;
import com.backbase.mambu.clients.model.Currency;
import com.backbase.mambu.clients.model.Currency.CodeEnum;
import com.backbase.mambu.clients.model.CustomFieldValueConfiguration;
import com.backbase.mambu.clients.model.CustomFieldValueGroupConfiguration;
import com.backbase.mambu.clients.model.CustomFieldValueSetConfiguration;
import com.backbase.mambu.clients.model.DepositAccount;
import com.backbase.mambu.clients.model.DepositAccount.AccountHolderTypeEnum;
import com.backbase.mambu.clients.model.DepositAccountAction;
import com.backbase.mambu.clients.model.DepositAccountDepositDetailsDepositAccount;
import com.backbase.mambu.clients.model.DepositAccountOverdraftSettings;
import com.backbase.mambu.clients.model.DepositTransactionInput;
import com.backbase.mambu.clients.model.DisbursementLoanTransactionInput;
import com.backbase.mambu.clients.model.InterestSettings;
import com.backbase.mambu.clients.model.InterestSettings.InterestApplicationMethodEnum;
import com.backbase.mambu.clients.model.InterestSettings.InterestBalanceCalculationMethodEnum;
import com.backbase.mambu.clients.model.InterestSettings.InterestCalculationMethodEnum;
import com.backbase.mambu.clients.model.InterestSettings.InterestChargeFrequencyEnum;
import com.backbase.mambu.clients.model.InterestSettings.InterestRateSourceEnum;
import com.backbase.mambu.clients.model.InterestSettings.InterestTypeEnum;
import com.backbase.mambu.clients.model.LoanAccount;
import com.backbase.mambu.clients.model.LoanAccount.AccountStateEnum;
import com.backbase.mambu.clients.model.LoanAccount.FuturePaymentsAcceptanceEnum;
import com.backbase.mambu.clients.model.LoanAccount.PaymentMethodEnum;
import com.backbase.mambu.clients.model.LoanAccountAction;
import com.backbase.mambu.clients.model.PatchOperation;
import com.backbase.mambu.clients.model.PatchOperation.OpEnum;
import com.backbase.mambu.clients.model.ScheduleSettings;
import com.backbase.mambu.clients.model.ScheduleSettings.GracePeriodTypeEnum;
import com.backbase.mambu.clients.model.ScheduleSettings.RepaymentPeriodUnitEnum;
import com.backbase.mambu.clients.model.ScheduleSettings.RepaymentScheduleMethodEnum;
import com.backbase.mambu.clients.model.ScheduleSettings.ScheduleDueDatesMethodEnum;
import com.backbase.mambu.clients.model.TransferDepositTransactionInput;
import com.backbase.mambu.clients.model.TransferDetailsInput;
import com.backbase.mambu.clients.model.TransferDetailsInput.LinkedAccountTypeEnum;
import com.backbase.mambu.clients.model.WithdrawalDepositTransactionInput;
import com.backbase.places.clients.model.PlaceAddress;
import com.backbase.places.clients.model.PlaceConfiguration;
import com.backbase.places.clients.model.PlaceCustomFieldValueSetConfiguration;
import com.backbase.productled.config.MambuConfigurationProperties;
import com.backbase.productled.model.AccountStatementEnum;
import com.backbase.productled.model.LoanDocumentEnum;
import com.backbase.stream.LegalEntityTask;
import com.backbase.stream.legalentity.model.BaseProduct;
import com.backbase.stream.legalentity.model.BaseProductState;
import com.backbase.stream.legalentity.model.CurrentAccount;
import com.backbase.stream.legalentity.model.JobProfileUser;
import com.backbase.stream.legalentity.model.LegalEntity;
import com.backbase.stream.legalentity.model.Loan;
import com.backbase.stream.legalentity.model.SavingsAccount;
import com.backbase.stream.legalentity.model.TermUnit;
import com.backbase.stream.worker.StreamTaskExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.cloud.sleuth.annotation.SpanTag;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpClientErrorException.BadRequest;
import org.springframework.web.client.RestClientException;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.backbase.mambu.clients.model.DepositAccountAction.ActionEnum.APPROVE;
import static com.backbase.mambu.clients.model.DepositAccountAction.ActionEnum.CLOSE;
import static com.backbase.mambu.clients.model.DepositAccountAction.ActionEnum.CLOSE_REJECT;
import static com.backbase.mambu.clients.model.DepositAccountAction.ActionEnum.CLOSE_WITHDRAW;
import static com.backbase.mambu.clients.model.DepositAccountAction.ActionEnum.LOCK;
import static com.backbase.mambu.clients.model.ScheduleSettings.RepaymentPeriodUnitEnum.DAYS;
import static com.backbase.mambu.clients.model.ScheduleSettings.RepaymentPeriodUnitEnum.MONTHS;
import static com.backbase.mambu.clients.model.ScheduleSettings.RepaymentPeriodUnitEnum.WEEKS;
import static com.backbase.mambu.clients.model.ScheduleSettings.RepaymentPeriodUnitEnum.YEARS;
import static com.backbase.stream.LegalEntitySaga.CREATED;
import static com.backbase.stream.legalentity.model.LegalEntityType.CUSTOMER;
import static org.springframework.data.mapping.Alias.ofNullable;
import static org.springframework.util.CollectionUtils.isEmpty;

/**
 * Orchestrate the business logic to create resources in Mambu
 */
@Slf4j
@AllArgsConstructor
@Service
public class MambuService implements StreamTaskExecutor<LegalEntityTask> {

    private final ClientsApi clientsApi;
    private final DepositAccountsApi depositAccountsApi;
    private final DepositTransactionsApi depositTransactionsApi;
    private final DocumentsApi documentsApi;
    private final LoanAccountsApi loanAccountsApi;
    private final LoanTransactionsApi loanTransactionsApi;
    private final CentresConfigurationApi centresConfigurationApi;
    private final MambuConfigurationProperties mambuConfigurationProperties;

    private static final String SPAN_ID = "create-mambu-resources";
    private static final String RESOURCE = "mambu";
    private static final String OPERATION = "create";
    private static final String DETAILS_LEVEL = "BASIC";
    private static final int TRANSACTIONS_AMOUNT = 9;
    private static final String SPACE = " ";
    private static final String PERSONAL = "Personal";
    private static final String WORK = "Work";
    private static final String MOBILE = "Mobile";
    private static final String HOME = "Home";
    private static final String DA_OWNER_TYPE = "DEPOSIT_ACCOUNT";
    private static final String LA_OWNER_TYPE = "LOAN_ACCOUNT";

    @NewSpan
    public Mono<LegalEntityTask> executeTask(@SpanTag(value = "streamTask") LegalEntityTask streamTask) {
        if (mambuConfigurationProperties.isBootstrapResourcesEnabled()) {
            return createMambuResources(streamTask);
        } else {
            log.warn("Flag 'mambu.bootstrapResourcesEnabled' is set to false. Skipping resource creation in Mambu");
            return Mono.just(streamTask);
        }
    }

    @Override
    public Mono<LegalEntityTask> rollBack(LegalEntityTask legalEntityTask) {
        return null;
    }

    @ContinueSpan(log = SPAN_ID)
    private Mono<LegalEntityTask> createMambuResources(@SpanTag(value = "streamTask") LegalEntityTask task) {
        task.info(RESOURCE, OPERATION, "", task.getData().getExternalId(), null,
            "Creating Mambu resources for Legal Entity with External ID: %s", task.getData().getExternalId());
        LegalEntity legalEntity = task.getData();

        // create profile of employee users in mambu
        if (BANK.equals(legalEntity.getLegalEntityType()) && !CollectionUtils.isEmpty(legalEntity.getUsers())) {
            processUsers(legalEntity);
        }

        // Create users and product groups from subsidiaries
        processSubsidiaries(legalEntity.getSubsidiaries());

        // Create places (branch or atm) from places-data file
        processPlaces();

        task.info(RESOURCE, OPERATION, CREATED, legalEntity.getExternalId(), legalEntity.getInternalId(),
            "Created Mambu resources for Legal Entity with External ID: %s", legalEntity.getExternalId());

        return Mono.just(task);
    }

    private void processSubsidiaries(List<LegalEntity> subsidiaries) {
        Optional.ofNullable(subsidiaries)
            .orElseGet(Collections::emptyList)
            .stream()
            .filter(Objects::nonNull)
            .forEach(legalEntity -> {
                if (legalEntity.getUsers() != null) {
                    processUsers(legalEntity);
                }
                if (legalEntity.getProductGroups() != null) {
                    processProductGroups(legalEntity);
                }
                processSubsidiaries(legalEntity.getSubsidiaries());
            });
    }

    private void processUsers(LegalEntity legalEntity) {
        legalEntity.getUsers().forEach(user -> {
            Client client = new Client()
                .id(user.getUser().getExternalId())
                .assignedBranchKey(mambuConfigurationProperties.getBranchKey())
                .firstName(user.getUser().getFullName().split(SPACE)[0])
                .lastName(user.getUser().getFullName().split(SPACE).length > 1 ?
                    user.getUser().getFullName().split(SPACE)[1] : "(PL)");

            ClientCustomFieldsClients customFieldsClients = new ClientCustomFieldsClients();
            updateClientEmailAddress(user, client, customFieldsClients);
            updateClientPhoneNumber(user, client, customFieldsClients);
            updateClientPostalAddress(user, client);
            client.setCustomFieldsClients(customFieldsClients);
            createOrUpdateClient(client);
        });
    }

    private void processProductGroups(LegalEntity legalEntity) {
        Client client = clientsApi.getById(legalEntity.getExternalId(), DETAILS_LEVEL);
        String clientKey = client.getEncodedKey();
        legalEntity.getProductGroups().forEach(productGroup -> {
            productGroup.getCurrentAccounts().forEach(
                currentAccount -> createDepositCurrentAccount(currentAccount, clientKey));
            productGroup.getSavingAccounts().forEach(
                savingsAccount -> createDepositSavingAccount(savingsAccount, clientKey));
            Optional.ofNullable(productGroup.getLoans()).ifPresent(sas -> sas
                .forEach(loanAccount -> createLoanAccount(loanAccount, client)));
        });
    }

    private void createLoanAccount(Loan product, Client client) {
        try {
            var loanAccount = loanAccountsApi.getById(product.getExternalId(), DETAILS_LEVEL);
            log.info("Found Loan account with id '{}' in branch with id '{}' for client {}", product.getExternalId(),
                mambuConfigurationProperties.getBranchKey(), client.getId());
            updateLoanBranchCode(product.getExternalId(), product.getInterestSettlementAccount());
            if (!loanAccount.getAccountHolderKey().equals(client.getId())) {
                log.warn("Loan account with id '{}' owner is client '{}', but was attempted to be created for '{}'",
                    product.getExternalId(), loanAccount.getAccountHolderKey(), client.getId());
            }
        } catch (HttpClientErrorException.NotFound e) {
            if (Objects.requireNonNull(e.getMessage()).contains("INVALID_LOAN_ACCOUNT_ID")) {
                log.info(
                    "Creating loan account with id '{}' and name '{}' for client with id '{}' in branch with id '{}'",
                    product.getExternalId(), product.getName(), client.getId(),
                    mambuConfigurationProperties.getBranchKey());
                var assertAmount = product.getPrincipalAmount().getAmount().divide(new BigDecimal("2"));
                var loanAccountBody = new LoanAccount()
                    .productTypeKey(product.getProductNumber())
                    .id(product.getExternalId())
                    .loanName(product.getName())
                    .loanAmount(product.getPrincipalAmount().getAmount())
                    .assets(List.of(new Asset().amount(assertAmount)
                            .assetName("Jacob Bontiusplaats 9, 1018 LL Amsterdam")
                            .originalAmount(assertAmount)
                            .originalCurrency(new Currency().code(CodeEnum.USD)),
                        new Asset().amount(assertAmount)
                            .assetName("bosch das 3000 adas calibration systems")
                            .originalAmount(assertAmount)
                            .originalCurrency(new Currency().code(CodeEnum.USD))))
                    .interestSettings(new InterestSettings()
                        .interestRate(product.getAccountInterestRate())
                        .interestRateSource(InterestRateSourceEnum.FIXED_INTEREST_RATE)
                        .accrueInterestAfterMaturity(false)
                        .interestApplicationMethod(InterestApplicationMethodEnum.AFTER_DISBURSEMENT)
                        .interestBalanceCalculationMethod(InterestBalanceCalculationMethodEnum.ONLY_PRINCIPAL)
                        .interestCalculationMethod(InterestCalculationMethodEnum.FLAT)
                        .interestChargeFrequency(InterestChargeFrequencyEnum.ANNUALIZED)
                        .interestType(InterestTypeEnum.SIMPLE_INTEREST))
                    .scheduleSettings(new ScheduleSettings()
                        .principalRepaymentInterval(1)
                        .gracePeriod(0)
                        .gracePeriodType(GracePeriodTypeEnum.NONE)
                        .scheduleDueDatesMethod(ScheduleDueDatesMethodEnum.INTERVAL)
                        .repaymentScheduleMethod(RepaymentScheduleMethodEnum.FIXED)
                        .repaymentInstallments(product.getTermNumber().intValue())
                        .repaymentPeriodCount(1)
                        .repaymentPeriodUnit(getRepaymentPeriodUnit(product.getTermUnit())))
                    .accountHolderType(LoanAccount.AccountHolderTypeEnum.CLIENT)
                    .accountHolderKey(client.getEncodedKey())
                    .accountState(AccountStateEnum.PENDING_APPROVAL)
                    .paymentMethod(PaymentMethodEnum.HORIZONTAL)
                    .allowOffset(false)
                    .arrearsTolerancePeriod(1)
                    .accruedInterest(BigDecimal.ZERO)
                    .futurePaymentsAcceptance(FuturePaymentsAcceptanceEnum.ACCEPT_OVERPAYMENTS);
                var loanAccount = loanAccountsApi.create(loanAccountBody, UUID.randomUUID().toString());
                loanAccountsApi.changeState(loanAccount.getId(), new LoanAccountAction().action(
                    LoanAccountAction.ActionEnum.APPROVE), UUID.randomUUID().toString());
                loanTransactionsApi.makeDisbursement(product.getExternalId(), new DisbursementLoanTransactionInput(),
                    UUID.randomUUID().toString());
                updateLoanBranchCode(product.getExternalId(), product.getInterestSettlementAccount());
                uploadAccountStatements(loanAccount.getId(), LA_OWNER_TYPE);
            }

        }
    }


    /**
     * Attach a document to a given deposit account in mambu
     *
     * @param depositAccountId Mambu deposit account id
     */
    private void uploadAccountStatements(String depositAccountId, String ownerType) {
        for (AccountStatementEnum statement : AccountStatementEnum.values()) {
            log.info("Attaching document {} to account {}", statement.getStatementName(), depositAccountId);
            documentsApi.createDocument(getFileFromResource(statement.getFilename()), UUID.randomUUID().toString(),
                ownerType, depositAccountId, statement.getStatementName(), statement.getStatementType());
        }
        if (ownerType.equals(LA_OWNER_TYPE)) {
            for (LoanDocumentEnum document : LoanDocumentEnum.values()) {
                log.info("Attaching Loan document {} to account {}", document.getDocumentName(), depositAccountId);
                documentsApi.createDocument(getFileFromResource(document.getFilename()), UUID.randomUUID().toString(),
                    ownerType, depositAccountId, document.getDocumentName(), document.getDocumentType());
            }
        }
    }

    private RepaymentPeriodUnitEnum getRepaymentPeriodUnit(TermUnit termUnit) {
        RepaymentPeriodUnitEnum result;
        switch (termUnit) {
            case DAILY:
                result = DAYS;
                break;
            case WEEKLY:
                result = WEEKS;
                break;
            case MONTHLY:
                result = MONTHS;
                break;
            default:
                result = YEARS;
                break;
        }
        return result;
    }

    private void updateLoanBranchCode(String id, String settlementAccountKey) {
        var operations = new java.util.ArrayList<>(
            List.of(new PatchOperation().op(OpEnum.ADD).path("/_details/branchCode")
                .value("67014822")));
        Optional.ofNullable(settlementAccountKey)
            .ifPresent(key -> {
                var result = depositAccountsApi.getByIdWithHttpInfo(settlementAccountKey, null);
                if (result.getStatusCode() == HttpStatus.OK) {
                    operations.add(new PatchOperation().op(OpEnum.ADD).path("settlementAccountKey")
                        .value(result.getBody().getEncodedKey()));
                }
            });
        loanAccountsApi.patch(id, operations);
        log.info("Bank branch code {} created/updated for this account id {}", "67014822", id);
    }

    private void updateClientPostalAddress(JobProfileUser user, Client client) {
        if (ofNullable(user.getUser().getUserProfile()).isPresent() &&
            !isEmpty(user.getUser().getUserProfile().getAddresses())) {
            user.getUser().getUserProfile().getAddresses()
                .forEach(address -> client.addAddressesItem(new Address()
                    .line1(address.getStreetAddress())
                    .city(address.getLocality())
                    .postcode(address.getPostalCode())
                    .country(address.getCountry())
                    .region(address.getRegion())));
        }
    }

    private void updateClientPhoneNumber(JobProfileUser user, Client client,
        ClientCustomFieldsClients customFieldsClients) {
        if (ofNullable(user.getUser().getMobileNumber()).isPresent() &&
            ofNullable(user.getUser().getMobileNumber().getNumber()).isPresent()) {

            if (WORK.equalsIgnoreCase(user.getUser().getMobileNumber().getType())) {
                client.mobilePhone2(user.getUser().getMobileNumber().getNumber());
                customFieldsClients.setPrimaryPhone(WORK);
            } else if (HOME.equalsIgnoreCase(user.getUser().getMobileNumber().getType())) {
                client.homePhone(user.getUser().getMobileNumber().getNumber());
                customFieldsClients.setPrimaryPhone(HOME);
            } else {
                client.mobilePhone(user.getUser().getMobileNumber().getNumber());
                customFieldsClients.setPrimaryPhone(MOBILE);
            }
        }
    }

    private void updateClientEmailAddress(JobProfileUser user, Client client,
        ClientCustomFieldsClients customFieldsClients) {
        if (ofNullable(user.getUser().getEmailAddress()).isPresent() &&
            ofNullable(user.getUser().getEmailAddress().getAddress()).isPresent()) {

            if (WORK.equalsIgnoreCase(user.getUser().getEmailAddress().getType())) {
                customFieldsClients.setWorkEmail(user.getUser().getEmailAddress().getAddress());
                customFieldsClients.setPrimaryEmail(WORK);
            } else {
                client.emailAddress(user.getUser().getEmailAddress().getAddress());
                customFieldsClients.setPrimaryEmail(PERSONAL);
            }
        }
    }

    /**
     * Create user with the given details
     */
    private void createOrUpdateClient(Client clientRequest) {
        try {
            Client client = clientsApi.getById(clientRequest.getId(), DETAILS_LEVEL);
            log.info("Found client with id '{}' in branch with id '{}'", clientRequest.getId(),
                mambuConfigurationProperties.getBranchKey());
            updateClient(clientRequest, client);

        } catch (HttpClientErrorException.NotFound e) {
            if (Objects.requireNonNull(e.getMessage()).contains("INVALID_CLIENT_ID")) {
                log.info("Creating client with id '{}' and name '{}' in branch with id '{}'", clientRequest.getId(),
                    clientRequest.getFirstName(), mambuConfigurationProperties.getBranchKey());
                clientsApi.create(clientRequest, UUID.randomUUID().toString());
            } else {
                throw e;
            }
        }
    }

    private void updateClient(Client clientRequest, Client client) {
        clientRequest.encodedKey(client.getEncodedKey())
            .assignedBranchKey(client.getAssignedBranchKey())
            .state(client.getState())
            .preferredLanguage(client.getPreferredLanguage())
            .clientRoleKey(client.getClientRoleKey())
            .assignedUserKey(client.getAssignedUserKey());
        clientsApi.update(client.getId(), clientRequest);
    }

    private void createDepositCurrentAccount(CurrentAccount currentAccount,
        String clientKey) {
        var overdraftSettings = new DepositAccountOverdraftSettings().allowOverdraft(true)
            .overdraftLimit(currentAccount.getAvailableBalance().getAmount());
        var detailsDepositAccount = new DepositAccountDepositDetailsDepositAccount()
            .bankBranchCode(currentAccount.getBankBranchCode())
            .bic(currentAccount.getBIC());

        createDepositAccount(currentAccount, clientKey,
            mambuConfigurationProperties.getCurrentAccountProductKey(), overdraftSettings, detailsDepositAccount);
    }

    private void createDepositSavingAccount(SavingsAccount savingsAccount,
        String clientKey) {
        var detailsDepositAccount = new DepositAccountDepositDetailsDepositAccount()
            .bankBranchCode(savingsAccount.getBankBranchCode())
            .bic(savingsAccount.getBIC());

        createDepositAccount(savingsAccount, clientKey,
            mambuConfigurationProperties.getSavingsAccountProductKey(), null, detailsDepositAccount);
    }

    /**
     * Create the given arrangement in Mambu for the given product type
     *
     * @param clientKey         Mambu user ID owner of the arrangement
     * @param productTypeKey    Mambu product ID
     * @param overdraftSettings Overdraft settings
     */
    private <T extends BaseProduct> void createDepositAccount(T product,
        String clientKey,
        String productTypeKey,
        DepositAccountOverdraftSettings overdraftSettings,
        DepositAccountDepositDetailsDepositAccount detailsDepositAccount) {
        try {
            DepositAccount depositAccount = depositAccountsApi.getById(product.getExternalId(), DETAILS_LEVEL);
            log.info("Found account with id '{}' in branch with id '{}'", product.getExternalId(),
                mambuConfigurationProperties.getBranchKey());

            updateDepositAccountCustomFields(product.getExternalId(), detailsDepositAccount);

            if (!depositAccount.getAccountHolderKey().equals(clientKey)) {
                log.warn("Account with id '{}' owner is client '{}', but was attempted to be created for '{}'",
                    product.getExternalId(), depositAccount.getAccountHolderKey(), clientKey);
            }
        } catch (HttpClientErrorException.NotFound e) {
            if (Objects.requireNonNull(e.getMessage()).contains("INVALID_DEPOSIT_ACCOUNT_ID")) {
                log.info(
                    "Creating deposit account with id '{}' and name '{}' for client with id '{}' in branch with id '{}'",
                    product.getExternalId(), product.getName(), clientKey, mambuConfigurationProperties.getBranchKey());
                DepositAccount depositAccount = new DepositAccount()
                    .id(product.getExternalId())
                    .name(product.getName())
                    .accountHolderKey(clientKey)
                    .productTypeKey(productTypeKey)
                    .accountHolderType(AccountHolderTypeEnum.CLIENT)
                    .currencyCode("USD")
                    .overdraftSettings(overdraftSettings)
                    .depositDetailsDepositAccount(detailsDepositAccount)
                    .assignedBranchKey(mambuConfigurationProperties.getBranchKey());
                depositAccount = depositAccountsApi.create(depositAccount, UUID.randomUUID().toString());

                BaseProductState productState = product.getState();
                Optional<BaseProductState> optionalState = Optional.ofNullable(productState);

                if (optionalState.isPresent()) {
                    if (CLOSE_REJECT.getValue().equals(productState.getState())) {
                        depositAccountsApi
                            .changeState(depositAccount.getEncodedKey(),
                                new DepositAccountAction().action(CLOSE_REJECT),
                                UUID.randomUUID().toString());
                        return;
                    }
                    if (CLOSE_WITHDRAW.getValue().equals(productState.getState())) {
                        depositAccountsApi
                            .changeState(depositAccount.getEncodedKey(),
                                new DepositAccountAction().action(CLOSE_WITHDRAW),
                                UUID.randomUUID().toString());
                        return;
                    }
                }
                depositAccountsApi
                    .changeState(depositAccount.getEncodedKey(), new DepositAccountAction().action(APPROVE),
                        UUID.randomUUID().toString());
                if (optionalState.isPresent()) {
                    if (CLOSE.getValue().equals(productState.getState())) {
                        fundAccount(depositAccount, BigDecimal.valueOf(0));

                        depositAccountsApi
                            .changeState(depositAccount.getEncodedKey(), new DepositAccountAction().action(CLOSE),
                                UUID.randomUUID().toString());
                        return;
                    }
                }
                // To ensure balance will be always enough for making the txns, sum txn amount * random factor used for txn amount
                fundAccount(depositAccount,
                    BigDecimal.valueOf(Math.random() * 10000 + (TRANSACTIONS_AMOUNT * 1000))
                        .setScale(2, RoundingMode.HALF_UP));
                if (optionalState.isPresent()) {
                    if (LOCK.getValue().equals(productState.getState())) {
                        depositAccountsApi
                            .changeState(depositAccount.getEncodedKey(), new DepositAccountAction().action(LOCK),
                                UUID.randomUUID().toString());
                        return;
                    }
                }
                if (productTypeKey.equals(mambuConfigurationProperties.getCurrentAccountProductKey())) {
                    createTransferTransactions(depositAccount);
                } else if (productTypeKey.equals(mambuConfigurationProperties.getSavingsAccountProductKey())) {
                    createWithdrawalTransactions(depositAccount);
                }
                uploadAccountStatements(depositAccount.getId(), DA_OWNER_TYPE);
            } else {
                throw e;
            }
        }
    }

    /**
     * Creates/Updates bank branch and bic codes in deposit account.
     *
     * @param depositAccountId Mambu deposit account id
     */
    private void updateDepositAccountCustomFields(String depositAccountId,
        DepositAccountDepositDetailsDepositAccount detailsDepositAccount) {
        if (Objects.nonNull(detailsDepositAccount.getBankBranchCode())) {
            depositAccountsApi.patch(depositAccountId,
                List.of(
                    new PatchOperation()
                        .op(OpEnum.ADD)
                        .path("/_Deposit_Details_Deposit_Account/bankBranchCode")
                        .value(detailsDepositAccount.getBankBranchCode())
                )
            );
            log.info("Bank branch code {} created/updated for this account id {}",
                detailsDepositAccount.getBankBranchCode(), depositAccountId);
        }

        if (Objects.nonNull(detailsDepositAccount.getBic())) {
            depositAccountsApi.patch(depositAccountId,
                List.of(
                    new PatchOperation()
                        .op(OpEnum.ADD)
                        .path("/_Deposit_Details_Deposit_Account/bic")
                        .value(detailsDepositAccount.getBic())
                ));
        }
        log.info("bic {} created/updated for this account id {}", detailsDepositAccount.getBic(), depositAccountId);
    }

    /**
     * Retrieve file from resource folder using file name
     *
     * @param fileName Name of file to be retrieved from resource folder
     * @return file object
     */
    @SneakyThrows
    private File getFileFromResource(String fileName) {
        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource(fileName);
        if (resource == null) {
            throw new IllegalArgumentException("file not found! " + fileName);
        } else {
            return new File(resource.toURI());
        }
    }

    private void fundAccount(DepositAccount account, BigDecimal amount) {
        log.info("Funding account {} with {} {}", account.getId(), amount, account.getCurrencyCode());
        try {
            depositTransactionsApi.makeDeposit(account.getEncodedKey(), new DepositTransactionInput().amount(amount),
                UUID.randomUUID().toString());
        } catch (BadRequest e) {
            log.error(String
                    .format("Bad request depositing into %s.\nResponse: %s", account.getId(), e.getResponseBodyAsString()),
                e);
        } catch (RestClientException e) {
            log.error(String.format("Unknown exception occurred depositing into%s ", account.getId()), e);
        }
    }

    private void createTransferTransactions(DepositAccount account) {
        log.info("Creating {} random transfer transactions for account {}", TRANSACTIONS_AMOUNT, account.getId());
        for (int i = 0; i < TRANSACTIONS_AMOUNT; i++) {
            depositTransactionsApi.makeTransfer(
                account.getEncodedKey(),
                new TransferDepositTransactionInput()
                    .amount(BigDecimal.valueOf(Math.random() * 1000).setScale(2, RoundingMode.HALF_UP))
                    .transferDetails(new TransferDetailsInput()
                        .linkedAccountType(LinkedAccountTypeEnum.DEPOSIT)
                        .linkedAccountKey(mambuConfigurationProperties.getTransactionsBeneficiaryAccountKey())),
                UUID.randomUUID().toString());
        }
    }

    private void createWithdrawalTransactions(DepositAccount account) {
        log.info("Creating {} random withdrawal transactions for account {}", TRANSACTIONS_AMOUNT, account.getId());
        for (int i = 0; i < TRANSACTIONS_AMOUNT; i++) {
            depositTransactionsApi.makeWithdrawal(
                account.getEncodedKey(),
                new WithdrawalDepositTransactionInput()
                    .amount(BigDecimal.valueOf(Math.random() * 1000).setScale(2, RoundingMode.HALF_UP)),
                UUID.randomUUID().toString());
        }
    }

    /**
     * Creates branch or atm in mambu Note: Mambu exposes only PUT endpoint. This request will remove any existing
     * branches/ATMs and creates what is sent in this body
     * TODO: (1) Investigate encoding issue in the GET call (2) Append new places to the existing
     */
    private void processPlaces() {

        if (!mambuConfigurationProperties.isIngestPlacesData()) {
            log.info("Skipping Places data ingestion as the ingestPlacesData flag is set to false");
            return;
        }
        File placesFile = getFileFromResource(mambuConfigurationProperties.getPlacesDataFile());
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            List<PlaceConfiguration> places = objectMapper.readValue(placesFile, new TypeReference<>() {
            });

            List<CentreConfiguration> centresList = Optional.of(places)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(Objects::nonNull)
                .map(place -> {
                    log.info("Mapping {} data to mambu object", place.getId());
                    CentreConfiguration centreConfiguration = new CentreConfiguration();
                    centreConfiguration.setId(place.getId());
                    centreConfiguration.setAssignedBranchId(place.getAssignedBranchId());
                    centreConfiguration.setName(place.getName());
                    centreConfiguration.setState(CentreConfiguration.StateEnum.fromValue(place.getState()));
                    AddressDetails addressDetails = processPlacesAddress(place.getAddress());
                    centreConfiguration.setAddress(addressDetails);
                    if (ofNullable(place.getCustomFieldValueSets()).isPresent()) {
                        List<CustomFieldValueSetConfiguration> convertedValueSets = processConvertedValueSets(
                            place.getCustomFieldValueSets());
                        centreConfiguration.setCustomFieldValueSets(convertedValueSets);
                    }
                    return centreConfiguration;
                })
                .collect(Collectors.toList());

            if (!CollectionUtils.isEmpty(centresList)) {
                CentresConfiguration centresConfiguration = new CentresConfiguration();
                centresConfiguration.setCentres(centresList);
                centresConfigurationApi.update(centresConfiguration);
                log.info("All the places data is added to mambu successfully");
            }
        } catch (IOException e) {
            log.error("Exception while ingesting places data into mambu", e);
        }
    }

    private List<CustomFieldValueSetConfiguration> processConvertedValueSets(
        List<PlaceCustomFieldValueSetConfiguration> customFieldValueSets) {

        return customFieldValueSets.stream().map(placeCustomFieldValueSet -> {
            CustomFieldValueSetConfiguration customFieldValueSetConfiguration = new CustomFieldValueSetConfiguration();
            customFieldValueSetConfiguration.setId(placeCustomFieldValueSet.getId());
            if (ofNullable(placeCustomFieldValueSet.getGroupedCustomFieldValues()).isPresent()) {
                List<CustomFieldValueGroupConfiguration> mambuGroupedCustomFieldValues = placeCustomFieldValueSet.getGroupedCustomFieldValues()
                    .stream().map(legalEntityGroupedCustomFieldValue -> {
                        CustomFieldValueGroupConfiguration customFieldValueGroupConfiguration = new CustomFieldValueGroupConfiguration();
                        customFieldValueGroupConfiguration.setIndex(legalEntityGroupedCustomFieldValue.getIndex());
                        List<CustomFieldValueConfiguration> mambuCustomFieldValueConfig = legalEntityGroupedCustomFieldValue.getCustomFieldValues()
                            .stream().map(placeCustomFieldValueConfiguration -> {
                                CustomFieldValueConfiguration customFieldValueConfiguration = new CustomFieldValueConfiguration();
                                customFieldValueConfiguration.setCustomFieldId(
                                    placeCustomFieldValueConfiguration.getCustomFieldId());
                                customFieldValueConfiguration.setValue(placeCustomFieldValueConfiguration.getValue());
                                return customFieldValueConfiguration;
                            }).collect(Collectors.toList());
                        customFieldValueGroupConfiguration.setCustomFieldValues(mambuCustomFieldValueConfig);
                        return customFieldValueGroupConfiguration;
                    }).collect(Collectors.toList());
                customFieldValueSetConfiguration.setGroupedCustomFieldValues(mambuGroupedCustomFieldValues);
            }
            if (ofNullable(placeCustomFieldValueSet.getStandardCustomFieldValues()).isPresent()) {
                List<CustomFieldValueConfiguration> mambuStandardCustomFieldValues = placeCustomFieldValueSet.getStandardCustomFieldValues()
                    .stream().map(legalEntityStandardCustomFieldValue -> {
                        CustomFieldValueConfiguration customFieldValueConfiguration = new CustomFieldValueConfiguration();
                        customFieldValueConfiguration.setValue(legalEntityStandardCustomFieldValue.getValue());
                        customFieldValueConfiguration.setCustomFieldId(
                            legalEntityStandardCustomFieldValue.getCustomFieldId());
                        return customFieldValueConfiguration;
                    }).collect(Collectors.toList());
                customFieldValueSetConfiguration.setStandardCustomFieldValues(mambuStandardCustomFieldValues);
            }
            return customFieldValueSetConfiguration;
        }).collect(Collectors.toList());
    }

    private AddressDetails processPlacesAddress(PlaceAddress address) {
        Function<PlaceAddress, AddressDetails> convertAddress = placeAddress -> {
            AddressDetails addressDetails = new AddressDetails();
            addressDetails.setCity(placeAddress.getCity());
            addressDetails.setCountry(placeAddress.getCountry());
            addressDetails.setLine1(placeAddress.getLine1());
            addressDetails.setLine2(placeAddress.getLine2());
            addressDetails.setPostcode(placeAddress.getPostcode());
            addressDetails.setRegion(placeAddress.getRegion());
            return addressDetails;
        };
        return convertAddress.apply(address);
    }
}