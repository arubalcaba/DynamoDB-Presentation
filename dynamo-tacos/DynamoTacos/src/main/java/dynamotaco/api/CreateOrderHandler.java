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
import java.util.*;
import java.util.stream.Collectors;
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
        addItemsToOrder(orderItem, order);

        PutItemRequest orderPutRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(orderItem)
                .build();
        dynamoDb.putItem(orderPutRequest);

    }

    private void addItemsToOrder(Map<String, AttributeValue> orderItem, Order order) {
        if(CollectionUtils.isNotEmpty(order.getTacos())) {
            List<Map<String, AttributeValue>> tacos = order.getTacos().stream().map(taco -> {
                Map<String, AttributeValue> tacoMap = new HashMap<>();
                tacoMap.put("TacoId", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
                tacoMap.put("MenuItemId", AttributeValue.builder().s(taco.getMenuItemId()).build());
                tacoMap.put("Name", AttributeValue.builder().s(taco.getName()).build());
                tacoMap.put("Price", AttributeValue.builder().n(String.valueOf(taco.getPrice())).build());

                if (taco.getToppings() != null) {
                    List<Map<String, AttributeValue>> toppings = taco.getToppings().stream().map(topping -> {
                        Map<String, AttributeValue> toppingMap = new HashMap<>();
                        toppingMap.put("ToppingId", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
                        toppingMap.put("Name", AttributeValue.builder().s(topping.getName()).build());
                        toppingMap.put("Price", AttributeValue.builder().n(String.valueOf(topping.getPrice())).build());
                        return toppingMap;
                    }).toList();
                    tacoMap.put("Toppings", AttributeValue.builder().l(toppings.stream().map(AttributeValue::fromM).collect(Collectors.toList())).build());
                }
                return tacoMap;
            }).toList();
            orderItem.put("Tacos", AttributeValue.builder().l(tacos.stream().map(AttributeValue::fromM).collect(Collectors.toList())).build());
        }

        if(CollectionUtils.isNotEmpty(order.getSideItems())) {
            List<Map<String, AttributeValue>> sideItems = order.getSideItems().stream().map(sideItem -> {
                Map<String, AttributeValue> sideItemMap = new HashMap<>();
                sideItemMap.put("SideItemId", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
                sideItemMap.put("Name", AttributeValue.builder().s(sideItem.getName()).build());
                sideItemMap.put("Price", AttributeValue.builder().n(String.valueOf(sideItem.getPrice())).build());
                return sideItemMap;
            }).toList();;
            orderItem.put("SideItems", AttributeValue.builder().l(sideItems.stream().map(AttributeValue::fromM).collect(Collectors.toList())).build());
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
