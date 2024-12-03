package dynamotaco.api;
import dynamotaco.models.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;


public class GetOrderHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            Map<String, String> queryParams = request.getQueryStringParameters();
            String email = queryParams.get("email");
            String orderId = queryParams.get("orderId");

            if (email == null || orderId == null) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("Missing email or orderId");
            }

            String partitionKey = "CUSTOMER#" + email;
            String sortKey = "ORDER#" + orderId;

            Map<String, AttributeValue> key = new HashMap<>();
            key.put("PK", AttributeValue.builder().s(partitionKey).build());
            key.put("SK", AttributeValue.builder().s(sortKey).build());

            GetItemRequest getItemRequest = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .build();

            GetItemResponse response = dynamoDb.getItem(getItemRequest);

            if (!response.hasItem()) {
                return new APIGatewayProxyResponseEvent()
                    .withStatusCode(404)
                    .withBody("Order not found");
            }

            Map<String, AttributeValue> item = response.item();
            Order order = mapToOrder(item);

            return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(Map.of(
                    "Access-Control-Allow-Headers", "*",
                    "Access-Control-Allow-Origin", "*",
                    "Access-Control-Allow-Methods", "*"
                ))
                .withBody(MAPPER.writeValueAsString(order));

        } catch (Exception e) {
            context.getLogger().log("Error retrieving order: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(500)
                .withBody("Error retrieving order");
        }
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

        List<Taco> tacos = getTacosForOrder(order.getId());
        List<SideItem> sideItems = getSideItemsForOrder(order.getId());
        order.setTacos(tacos);
        order.setSideItems(sideItems);

        return order;
    }

    private List<Taco> getTacosForOrder(String orderId) {
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :orderPK and begins_with(SK, :tacoPrefix)")
                .expressionAttributeValues(Map.of(
                        ":orderPK", AttributeValue.builder().s("ORDER#" + orderId).build(),
                        ":tacoPrefix", AttributeValue.builder().s("TACO#").build()
                ))
                .build();

        List<Taco> tacos = new ArrayList<>();
        QueryResponse response = dynamoDb.query(queryRequest);

        for (Map<String, AttributeValue> item : response.items()) {
            Taco taco = new Taco();
            taco.setId(item.get("SK").s().replace("TACO#", ""));
            taco.setName(item.get("Name").s());
            taco.setPrice(Double.parseDouble(item.get("Price").n()));
            List<Topping> toppings = getToppingsForTaco(taco.getId());
            taco.setToppings(toppings);
            tacos.add(taco);
        }

        return tacos;
    }

    private List<Topping> getToppingsForTaco(String tacoId) {
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :tacoPK and begins_with(SK, :toppingPrefix)")
                .expressionAttributeValues(Map.of(
                        ":tacoPK", AttributeValue.builder().s("TACO#" + tacoId).build(),
                        ":toppingPrefix", AttributeValue.builder().s("TOPPING#").build()
                ))
                .build();

        List<Topping> toppings = new ArrayList<>();
        QueryResponse response = dynamoDb.query(queryRequest);

        for (Map<String, AttributeValue> item : response.items()) {
            Topping topping = new Topping();
            topping.setId(item.get("SK").s().replace("TOPPING#", ""));
            topping.setName(item.get("Name").s());
            topping.setPrice(Double.parseDouble(item.get("Price").n()));
            toppings.add(topping);
        }

        return toppings;
    }


    private List<SideItem> getSideItemsForOrder(String orderId) {
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :orderPK and begins_with(SK, :sidePrefix)")
                .expressionAttributeValues(Map.of(
                        ":orderPK", AttributeValue.builder().s("ORDER#" + orderId).build(),
                        ":sidePrefix", AttributeValue.builder().s("SIDEITEM#").build()
                ))
                .build();

        List<SideItem> sideItems = new ArrayList<>();
        QueryResponse response = dynamoDb.query(queryRequest);

        for (Map<String, AttributeValue> item : response.items()) {
            SideItem sideItem = new SideItem();
            sideItem.setId(item.get("SK").s().replace("SIDEITEM#", ""));
            sideItem.setName(item.get("Name").s());
            sideItem.setPrice(Double.parseDouble(item.get("Price").n()));
            sideItems.add(sideItem);
        }

        return sideItems;
    }

    private List<Order> getAllOrders(String email) {
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.builder().s("CUSTOMER#" + email).build(),
                        ":skPrefix", AttributeValue.builder().s("ORDER#").build()
                ))
                .build();

        QueryResponse response = dynamoDb.query(queryRequest);

        return response.items().stream()
                .map(this::mapToOrder)
                .collect(Collectors.toList());
    }

}