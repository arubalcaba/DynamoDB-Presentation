package dynamotaco.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import dynamotaco.models.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class GetAllOrdersHandler  implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private Context loggingContext;


    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            loggingContext = context;
            loggingContext.getLogger().log("Retrieving orders");
            Map<String, String> queryParams = request.getQueryStringParameters();
            String email = queryParams.get("email");

            if (email == null) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("Missing email");
            }

            List<Order> orders = getAllOrders(email);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of(
                            "Access-Control-Allow-Headers", "*",
                            "Access-Control-Allow-Origin", "*",
                            "Access-Control-Allow-Methods", "*"
                    ))
                    .withBody(MAPPER.writeValueAsString(orders));
        } catch (Exception e) {
            context.getLogger().log("Error retrieving ordesr: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("Error retrieving orders");
        }

    }

    private List<Order> getAllOrders(String email) {
        loggingContext.getLogger().log("Retrieving orders for email: " + email);
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.builder().s("CUSTOMER#" + email).build(),
                        ":skPrefix", AttributeValue.builder().s("ORDER#").build()
                ))
                .build();

        QueryResponse response = dynamoDb.query(queryRequest);

        List<Order> orders = response.items().stream()
                .map(this::mapToOrder)
                .collect(Collectors.toList());


        enrichOrdersWithDetails(orders);

        return orders;
    }

    private void enrichOrdersWithDetails(List<Order> orders) {
        //log all the orderIds using context logger
        orders.forEach(order -> loggingContext.getLogger().log("Order ID: " + order.getId()));
        // 1. Get all order IDs
        List<String> orderIds = orders.stream()
                .map(Order::getId)
                .collect(Collectors.toList());

        // 2. Batch get tacos and side items
        Map<String, List<Taco>> tacosByOrderId = batchGetTacos(orderIds);
        //log all tacos in the map
        tacosByOrderId.forEach((key, value) -> loggingContext.getLogger().log("Tacos: " + value));
        Map<String, List<SideItem>> sideItemsByOrderId = batchGetSideItems(orderIds);
        //log all side items in the map
        sideItemsByOrderId.forEach((key, value) -> loggingContext.getLogger().log("Side Items: " + value));

        // 3. Get all taco IDs
        List<String> tacoIds = tacosByOrderId.values().stream()
                .flatMap(Collection::stream)
                .map(Taco::getId)
                .collect(Collectors.toList());

        // 4. Batch get toppings
        Map<String, List<Topping>> toppingsByTacoId = batchGetToppings(tacoIds);

        // 5. Associate everything back
        for (Order order : orders) {
            order.setTacos(tacosByOrderId.getOrDefault(order.getId(), new ArrayList<>()));
            order.setSideItems(sideItemsByOrderId.getOrDefault(order.getId(), new ArrayList<>()));

            // Associate toppings with their tacos
            order.getTacos().forEach(taco ->
                    taco.setToppings(toppingsByTacoId.getOrDefault(taco.getId(), new ArrayList<>()))
            );
        }
    }

    private Map<String, List<Taco>> batchGetTacos(List<String> orderIds) {
        Map<String, List<Taco>> tacosByOrderId = new HashMap<>();

        //log all orderIds
        orderIds.forEach(orderId -> loggingContext.getLogger().log(" batchGetTacos Order ID: " + orderId));

        for (List<String> batch : partition(orderIds, 100)) {
            List<Map<String, AttributeValue>> keys = batch.stream()
                    .map(orderId -> Map.of(
                            "PK", AttributeValue.builder().s("ORDER#" + orderId).build(),
                            "SK", AttributeValue.builder().s("TACO#").build()
                    ))
                    .collect(Collectors.toList());
            //log all keys
            loggingContext.getLogger().log("Table Name: " + TABLE_NAME);
            keys.forEach(key -> loggingContext.getLogger().log("Keys: " + key));

            BatchGetItemRequest request = BatchGetItemRequest.builder()
                    .requestItems(Map.of(
                            TABLE_NAME, KeysAndAttributes.builder()
                                    .keys(keys)
                                    .build()
                    ))
                    .build();

            BatchGetItemResponse result = dynamoDb.batchGetItem(request);
            loggingContext.getLogger().log("Fixing to log all the items");
            result.responses().get(TABLE_NAME).forEach(item -> {
                loggingContext.getLogger().log("Item: " + item);
            });

            // Process responses and group by order ID
            result.responses().get(TABLE_NAME).forEach(item -> {
                String orderId = item.get("PK").s().replace("ORDER#", "");
                Taco taco = mapTaco(item);
                tacosByOrderId
                        .computeIfAbsent(orderId, k -> new ArrayList<>())
                        .add(taco);
            });
        }

        return tacosByOrderId;
    }

    private Map<String, List<SideItem>> batchGetSideItems(List<String> orderIds) {
        Map<String, List<SideItem>> sideItemsByOrderId = new HashMap<>();
        for (List<String> batch : partition(orderIds, 100)) {
            List<Map<String, AttributeValue>> keys = batch.stream()
                    .map(orderId -> Map.of(
                            "PK", AttributeValue.builder().s("ORDER#" + orderId).build(),
                            "SK", AttributeValue.builder().s("SIDEITEM#").build()
                    ))
                    .collect(Collectors.toList());

            BatchGetItemRequest request = BatchGetItemRequest.builder()
                    .requestItems(Map.of(
                            TABLE_NAME, KeysAndAttributes.builder()
                                    .keys(keys)
                                    .build()
                    ))
                    .build();

            BatchGetItemResponse result = dynamoDb.batchGetItem(request);

            // Process responses and group by order ID
            result.responses().get(TABLE_NAME).forEach(item -> {
                String orderId = item.get("PK").s().replace("ORDER#", "");
                SideItem sideItem = mapSideItem(item);
                sideItemsByOrderId
                        .computeIfAbsent(orderId, k -> new ArrayList<>())
                        .add(sideItem);
            });
        }
      return sideItemsByOrderId;
    }

    private Map<String, List<Topping>> batchGetToppings(List<String> tacoIds) {
        Map<String, List<Topping>> toppingsByTacoId = new HashMap<>();
        for (List<String> batch : partition(tacoIds, 100)) {
            List<Map<String, AttributeValue>> keys = batch.stream()
                    .map(tacoId -> Map.of(
                            "PK", AttributeValue.builder().s("TACO#" + tacoId).build(),
                            "SK", AttributeValue.builder().s("TOPPING#").build()
                    ))
                    .collect(Collectors.toList());

            BatchGetItemRequest request = BatchGetItemRequest.builder()
                    .requestItems(Map.of(
                            TABLE_NAME, KeysAndAttributes.builder()
                                    .keys(keys)
                                    .build()
                    ))
                    .build();

            BatchGetItemResponse result = dynamoDb.batchGetItem(request);

            // Process responses and group by taco ID
            result.responses().get(TABLE_NAME).forEach(item -> {
                String tacoId = item.get("PK").s().replace("TACO#", "");
                Topping topping = mapTopping(item);
                toppingsByTacoId
                        .computeIfAbsent(tacoId, k -> new ArrayList<>())
                        .add(topping);
            });
        }
        return toppingsByTacoId;
    }

    private Taco mapTaco(Map<String, AttributeValue> item) {
        Taco taco = new Taco();
        taco.setId(item.get("SK").s().replace("TACO#", ""));
        taco.setName(item.get("Name").s());
        taco.setPrice(Double.parseDouble(item.get("Price").n()));
        return taco;
    }
    private SideItem mapSideItem(Map<String, AttributeValue> item) {
        SideItem sideItem = new SideItem();
        sideItem.setId(item.get("SK").s().replace("SIDEITEM#", ""));
        sideItem.setName(item.get("Name").s());
        sideItem.setPrice(Double.parseDouble(item.get("Price").n()));
        return sideItem;
    }

    private Topping mapTopping(Map<String, AttributeValue> item) {
        Topping topping = new Topping();
        topping.setId(item.get("SK").s().replace("TOPPING#", ""));
        topping.setName(item.get("Name").s());
        topping.setPrice(Double.parseDouble(item.get("Price").n()));
        return topping;
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        return IntStream.range(0, (list.size() + size - 1) / size)
                .mapToObj(i -> list.subList(i * size, Math.min(list.size(), (i + 1) * size)))
                .collect(Collectors.toList());
    }

    private Order mapToOrder(Map<String, AttributeValue> item) {
        Order order = new Order();
        String pk = item.get("PK").s();
        String sk = item.get("SK").s();

        order.setCustomerId(pk.replace("CUSTOMER#", ""));
        order.setId(sk.replace("ORDER#", ""));
        var zoneDate = ZonedDateTime.parse(item.get("OrderDate").s());
        order.setOrderDate(Date.from(zoneDate.toInstant()));
        order.setTotalPrice(Double.parseDouble(item.get("TotalPrice").n()));
        order.setStatus(OrderStatus.valueOf(item.get("Status").s()));

//        List<Taco> tacos = getTacosForOrder(order.getId());
//        List<SideItem> sideItems = getSideItemsForOrder(order.getId());
//        order.setTacos(tacos);
//        order.setSideItems(sideItems);

        return order;
    }


}
