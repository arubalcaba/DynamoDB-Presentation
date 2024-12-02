package dynamotaco.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import dynamotaco.models.Order;
import dynamotaco.models.SideItem;
import dynamotaco.models.Topping;
import org.apache.commons.collections4.CollectionUtils;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class CreateOrderHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            Order order = MAPPER.readValue(request.getBody(), Order.class);
            context.getLogger().log("Raw input: " + order.toString());
            String customerId = order.getCustomerId();
            String orderId = Optional.ofNullable(order.getId())
                    .orElse(UUID.randomUUID().toString());
            String partitionKey = "CUSTOMER#" + customerId;
            String sortKey = "ORDER#" + orderId;

            order.setId(orderId);
            createOrderRecord(partitionKey, sortKey, order);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(201)
                    .withHeaders(Map.of(
                            "Access-Control-Allow-Headers", "*",
                            "Access-Control-Allow-Origin", "*",
                            "Access-Control-Allow-Methods", "*"
                    ))
                    .withBody("Order with ID " + orderId + " created successfully.");


        } catch (Exception e) {
            context.getLogger().log("Error creating order: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("Error creating order");
        }
    }

    private void createOrderRecord(String partitionKey, String sortKey, Order order) {
        Map<String, AttributeValue> orderItem = new HashMap<>();
        var currentTimeStamp = ZonedDateTime.now(ZoneOffset.UTC).toString();
        orderItem.put("PK", AttributeValue.builder().s(partitionKey).build());
        orderItem.put("SK", AttributeValue.builder().s(sortKey).build());
        orderItem.put("OrderDate", AttributeValue.builder().s(currentTimeStamp).build());
        var totalPrice = calculateTotalPrice(order);
        orderItem.put("TotalPrice", AttributeValue.builder().n(String.valueOf(totalPrice)).build());
        orderItem.put("Status", AttributeValue.builder().s(order.getStatus().toString()).build());

        PutItemRequest orderPutRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(orderItem)
                .build();
        dynamoDb.putItem(orderPutRequest);

        // Add Tacos and SideItems to the order
        addItemsToOrder(order);

    }

    private void addItemsToOrder(Order order) {
        if(CollectionUtils.isNotEmpty(order.getTacos())) {
            order.getTacos().forEach(taco -> {
                // Add taco to order
                String tacoId = UUID.randomUUID().toString();
                String tacoPartitionKey = "ORDER#" + order.getId();
                String tacoSortKey = "TACO#" + tacoId;
                Map<String, AttributeValue> tacoItem = new HashMap<>();
                tacoItem.put("PK", AttributeValue.builder().s(tacoPartitionKey).build());
                tacoItem.put("SK", AttributeValue.builder().s(tacoSortKey).build());
                tacoItem.put("MenuItemId", AttributeValue.builder().s(taco.getMenuItemId()).build());
                tacoItem.put("Name", AttributeValue.builder().s(taco.getName()).build());
                tacoItem.put("Price", AttributeValue.builder().n(String.valueOf(taco.getPrice())).build());

                PutItemRequest tacoPutRequest = PutItemRequest.builder()
                        .tableName(TABLE_NAME)
                        .item(tacoItem)
                        .build();
                dynamoDb.putItem(tacoPutRequest);


                if(CollectionUtils.isNotEmpty(taco.getToppings())){
                    taco.getToppings().forEach(topping -> {
                        // Add taco topping to order
                        String toppingId = UUID.randomUUID().toString();
                        String toppingPartitionKey = "TACO#" + tacoId;
                        String toppingSortKey = "TOPPING#" + toppingId;
                        Map<String, AttributeValue> toppingItem = new HashMap<>();
                        toppingItem.put("PK", AttributeValue.builder().s(toppingPartitionKey).build());
                        toppingItem.put("SK", AttributeValue.builder().s(toppingSortKey).build());
                        toppingItem.put("MenuItemId", AttributeValue.builder().s(topping.getMenuItemId()).build());
                        toppingItem.put("Name", AttributeValue.builder().s(topping.getName()).build());
                        toppingItem.put("Price", AttributeValue.builder().n(String.valueOf(topping.getPrice())).build());

                        PutItemRequest toppingPutRequest = PutItemRequest.builder()
                                .tableName(TABLE_NAME)
                                .item(toppingItem)
                                .build();
                        dynamoDb.putItem(toppingPutRequest);
                    });
                }
            });
        }

        if(CollectionUtils.isNotEmpty(order.getSideItems())) {
            order.getSideItems().forEach(sideItem -> {
                // Add side item to order
                String sideItemId = UUID.randomUUID().toString();
                String sideItemPartitionKey = "ORDER#" + order.getId();
                String sideItemSortKey = "SIDEITEM#" + sideItemId;
                Map<String, AttributeValue> sideItemItem = new HashMap<>();
                sideItemItem.put("PK", AttributeValue.builder().s(sideItemPartitionKey).build());
                sideItemItem.put("SK", AttributeValue.builder().s(sideItemSortKey).build());
                sideItemItem.put("MenuItemId", AttributeValue.builder().s(sideItem.getMenuItemId()).build());
                sideItemItem.put("Name", AttributeValue.builder().s(sideItem.getName()).build());
                sideItemItem.put("Price", AttributeValue.builder().n(String.valueOf(sideItem.getPrice())).build());

                PutItemRequest sideItemPutRequest = PutItemRequest.builder()
                        .tableName(TABLE_NAME)
                        .item(sideItemItem)
                        .build();
                dynamoDb.putItem(sideItemPutRequest);
            });
        }

    }

    private double calculateTotalPrice(Order order) {
        return Stream.ofNullable(order.getTacos())
                .flatMapToDouble(tacos -> tacos.stream()
                        .mapToDouble(taco -> taco.getPrice() +
                                Stream.ofNullable(taco.getToppings())
                                        .flatMap(toppings -> toppings.stream())
                                        .mapToDouble(Topping::getPrice)
                                        .sum()))
                .sum() +
                Stream.ofNullable(order.getSideItems())
                        .flatMap(sideItems -> sideItems.stream())
                        .mapToDouble(SideItem::getPrice)
                        .sum();
    }
}
