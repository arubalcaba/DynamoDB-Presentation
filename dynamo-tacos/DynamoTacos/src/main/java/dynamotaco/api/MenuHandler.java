package dynamotaco.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import dynamotaco.models.FoodItemType;
import dynamotaco.models.MenuItem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MenuHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // Query DynamoDB for items with PK = "MENU"
            Map<String, AttributeValue> expressionValues = new HashMap<>();
            expressionValues.put(":menuPartition", AttributeValue.builder().s("MENU").build());

            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .keyConditionExpression("PK = :menuPartition")
                    .expressionAttributeValues(expressionValues)
                    .build();

            QueryResponse queryResponse = dynamoDb.query(queryRequest);
            List<Map<String, AttributeValue>> items = queryResponse.items();

            // Convert DynamoDB items to MenuItem objects
            List<MenuItem> menuItems = items.stream()
                    .map(MenuHandler::convertToMenuItem)
                    .collect(Collectors.toList());

            String jsonResponse = MAPPER.writeValueAsString(menuItems);

            // Return response with JSON payload
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(jsonResponse);
        } catch (Exception e) {
            context.getLogger().log("Error fetching menu items: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("Error fetching menu items");
        }
    }

    // Helper method to convert DynamoDB item to MenuItem object
    private static MenuItem convertToMenuItem(Map<String, AttributeValue> item) {
        MenuItem menuItem = new MenuItem();
        menuItem.setId(item.get("id").s());
        menuItem.setName(item.get("name").s());
        menuItem.setPrice(Double.parseDouble(item.get("price").n()));
        menuItem.setDescription(item.get("description") != null ? item.get("description").s() : null);

        // Parse FoodItemType from string, default to null if not set
        if (item.containsKey("foodItemType")) {
            menuItem.setFoodItemType(FoodItemType.valueOf(item.get("foodItemType").s()));
        }

        return menuItem;
    }
}
