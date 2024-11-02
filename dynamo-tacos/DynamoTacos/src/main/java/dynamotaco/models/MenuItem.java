package dynamotaco.models;

public class MenuItem extends BaseItem {
    private FoodItemType foodItemType;
    private String description;

    public FoodItemType getFoodItemType() {
        return foodItemType;
    }

    public void setFoodItemType(FoodItemType foodItemType) {
        this.foodItemType = foodItemType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
