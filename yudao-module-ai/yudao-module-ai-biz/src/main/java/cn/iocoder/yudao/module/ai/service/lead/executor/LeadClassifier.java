package cn.iocoder.yudao.module.ai.service.lead.executor;

import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.ClassificationResult;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.CrawlResult;
import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.ExtractionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rule-based target-customer classifier.
 */
public class LeadClassifier {

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = Map.ofEntries(
            Map.entry("wall_mounted_washing_machine", List.of("wall mounted washing machine", "wall-mounted washing machine",
                    "wall mounted washer", "mini washing machine", "compact washing machine", "compact laundry appliance",
                    "compact washer", "small appliance", "home appliance")),
            Map.entry("yacht_and_marine", List.of("yacht sales", "yacht dealer", "yacht broker", "yacht manufacturer",
                    "yacht club", "yacht maintenance", "yacht equipment", "yacht accessories", "luxury yacht",
                    "yacht fitting", "marine accessories", "marine supplies", "marine equipment", "boat equipment",
                    "houseboat", "ship interior", "ship fit out", "boat galley", "marine appliances")),
            Map.entry("camper_and_rv", List.of("motorhome sales", "rv sales", "camper van dealer", "caravan dealer",
                    "motorhome dealer", "rv manufacturer", "camper van conversion", "camper van accessories",
                    "camper accessories", "rv equipment", "rv accessories", "caravan accessories", "campsite operator",
                    "outdoor equipment", "camping equipment", "camping appliances", "compact appliances for vans")),
            Map.entry("appliance_dealers", List.of("home appliance dealer", "home appliance distributor",
                    "home appliance wholesaler", "home appliance importer", "home appliance agent",
                    "home appliance retailer", "kitchen appliance", "bathroom appliance", "electrical appliance",
                    "appliance chain", "smart home", "home goods importer")),
            Map.entry("hotel_and_serviced_apartment", List.of("boutique hotel", "hotel chain", "high end guesthouse",
                    "guesthouse", "serviced apartment", "long stay apartment", "hotel supplies", "hotel equipment",
                    "hotel engineering", "hotel renovation", "smart room", "guest room supplies")),
            Map.entry("real_estate_and_interior", List.of("apartment developer", "real estate developer",
                    "furnished apartment", "interior design", "home renovation", "modular housing",
                    "container housing", "tiny house")));
    private static final List<String> B2B_SIGNALS = List.of(
            "b2b", "wholesale", "distributor", "distribution", "dealer", "dealers", "sales", "retailer",
            "reseller", "purchasing", "procurement", "haendler", "händler", "vertrieb", "dystrybucja");
    private static final List<String> COMMERCE_SIGNALS = List.of(
            "shop", "store", "retailer", "dealer", "distributor", "supplier", "manufacturer", "sales company",
            "broker", "hotel", "guesthouse", "serviced apartment", "apartment operator", "developer",
            "renovation", "interior design", "conversion company", "ecommerce", "online shop", "local store");
    private static final List<String> NON_TARGET_SIGNALS = List.of("news", "blog", "forum", "job board", "career",
            "yellow pages", "directory", "classifieds");
    private static final List<String> AGGREGATOR_SIGNALS = List.of("marketplace", "price comparison", "reviews of",
            "top 10", "best products", "directory");
    private static final Map<String, String> CUSTOMER_TYPE_BY_CATEGORY = Map.of(
            "wall_mounted_washing_machine", "home appliance retailer/distributor",
            "yacht_and_marine", "yacht sales, marine equipment, or yacht service company",
            "camper_and_rv", "RV, motorhome, camper, caravan, or camping equipment company",
            "appliance_dealers", "home appliance dealer, distributor, importer, or retailer",
            "hotel_and_serviced_apartment", "hotel, serviced apartment, or guest room procurement customer",
            "real_estate_and_interior", "real estate, interior, renovation, or compact housing customer");

    public ClassificationResult classifySite(CrawlResult crawlResult, ExtractionResult extraction, String hintCategory) {
        String pageText = String.join(" ", crawlResult.getPages().stream()
                .map(page -> (page.getTitle() == null ? "" : page.getTitle()) + " " + (page.getText() == null ? "" : page.getText()))
                .toList());
        String text = (pageText + " " + String.join(" ", extraction.getMainProducts())).toLowerCase();
        boolean largePlatform = LeadAgentUtils.isBlockedDomain(crawlResult.getDomain()) || containsAny(text, AGGREGATOR_SIGNALS);
        boolean nonTarget = containsAny(text, NON_TARGET_SIGNALS);
        String matchedCategory = matchCategory(text, hintCategory);
        boolean productRelevanceHigh = matchedCategory != null && categoryHitCount(text, matchedCategory) >= 2;
        boolean hasB2bSignal = containsAny(text, B2B_SIGNALS);
        boolean hasCommerceSignal = containsAny(text, COMMERCE_SIGNALS);
        boolean hasContactSignal = !extraction.getEmails().isEmpty() || LeadAgentUtils.hasText(extraction.getPhone())
                || LeadAgentUtils.hasText(extraction.getContactPage());
        boolean hasAddressOrImprint = text.contains("impressum") || text.contains("address:")
                || crawlResult.getPages().stream().anyMatch(page -> "imprint".equals(page.getPageType()));
        boolean productUnrelated = matchedCategory == null;
        boolean target = matchedCategory != null && hasContactSignal && hasCommerceSignal && !largePlatform && !nonTarget;

        List<String> reasons = new ArrayList<>();
        if (matchedCategory != null) {
            reasons.add("matched category: " + matchedCategory);
        }
        if (productRelevanceHigh) {
            reasons.add("strong product relevance");
        }
        if (hasB2bSignal) {
            reasons.add("B2B/wholesale/dealer signal found");
        }
        if (hasContactSignal) {
            reasons.add("business contact details found");
        }
        if (hasAddressOrImprint) {
            reasons.add("address or imprint signal found");
        }
        if (largePlatform) {
            reasons.add("large platform or aggregator signal");
        }
        if (nonTarget) {
            reasons.add("non-target content signal");
        }
        if (reasons.isEmpty()) {
            reasons.add("insufficient product and contact signals");
        }

        return ClassificationResult.builder()
                .target(target)
                .targetCustomerType(CUSTOMER_TYPE_BY_CATEGORY.get(matchedCategory))
                .matchedCategory(matchedCategory)
                .reason(String.join("; ", reasons))
                .productRelevanceHigh(productRelevanceHigh)
                .hasB2bSignal(hasB2bSignal)
                .hasAddressOrImprint(hasAddressOrImprint)
                .largePlatformOrAggregator(largePlatform)
                .productUnrelated(productUnrelated)
                .build();
    }

    private String matchCategory(String text, String hintCategory) {
        if (LeadAgentUtils.hasText(hintCategory) && categoryHitCount(text, hintCategory) > 0) {
            return hintCategory;
        }
        String bestCategory = null;
        int bestScore = 0;
        for (String category : CATEGORY_KEYWORDS.keySet()) {
            int score = categoryHitCount(text, category);
            if (score > bestScore) {
                bestScore = score;
                bestCategory = category;
            }
        }
        return bestScore > 0 ? bestCategory : null;
    }

    private int categoryHitCount(String text, String category) {
        return (int) CATEGORY_KEYWORDS.getOrDefault(category, List.of()).stream().filter(text::contains).count();
    }

    private boolean containsAny(String text, List<String> signals) {
        return signals.stream().anyMatch(text::contains);
    }

}
