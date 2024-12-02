package dynamotaco.models;

import java.util.List;

public class Taco extends BaseItem{
    private String menuItemId;
    private List<Topping> toppings;

    public String getMenuItemId() {
        return menuItemId;
    }

    public void setMenuItemId(String menuItemId) {
        this.menuItemId = menuItemId;
    }

    public List<Topping> getToppings() {
        return toppings;
    }
    public void setToppings(List<Topping> toppings) {
        this.toppings = toppings;
    }
}
