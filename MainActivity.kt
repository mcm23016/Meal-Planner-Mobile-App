package com.example.mealplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// -------------------- DataStore setup --------------------
// Top-level DataStore delegate
val Context.dataStore by preferencesDataStore(name = "meal_planner")

object MealStorage {
    private val RECIPES_KEY = stringPreferencesKey("recipes")
    private val WEEK_MEALS_KEY = stringPreferencesKey("week_meals")
    private val gson = Gson()

    suspend fun saveRecipes(context: Context, recipes: List<MealData>) {
        val json = gson.toJson(recipes)
        context.dataStore.edit { prefs ->
            prefs[RECIPES_KEY] = json
        }
    }

    fun loadRecipes(context: Context) = context.dataStore.data
        .map<Preferences, List<MealData>> { prefs ->
            prefs[RECIPES_KEY]?.let {
                val type = object : TypeToken<List<MealData>>() {}.type
                gson.fromJson(it, type)
            } ?: emptyList()
        }

    suspend fun saveWeekMeals(context: Context, weekMeals: List<DayMeals>) {
        val weekMealsPlain = weekMeals.map { day ->
            mapOf(
                "day" to day.day,
                "breakfast" to day.breakfast.value,
                "lunch" to day.lunch.value,
                "dinner" to day.dinner.value
            )
        }
        val json = gson.toJson(weekMealsPlain)
        context.dataStore.edit { prefs ->
            prefs[WEEK_MEALS_KEY] = json
        }
    }

    fun loadWeekMeals(context: Context) = context.dataStore.data
        .map<Preferences, List<DayMeals>> { prefs ->
            prefs[WEEK_MEALS_KEY]?.let {
                val type = object : TypeToken<List<Map<String, String>>>() {}.type
                val list: List<Map<String, String>> = gson.fromJson(it, type)
                list.map { map ->
                    DayMeals(
                        day = map["day"] ?: "",
                        breakfast = mutableStateOf(map["breakfast"] ?: "None"),
                        lunch = mutableStateOf(map["lunch"] ?: "None"),
                        dinner = mutableStateOf(map["dinner"] ?: "None")
                    )
                }
            } ?: days.map { DayMeals(it) }
        }
}

// -------------------- Data classes --------------------
data class MealData(
    val name: String,
    val description: String,
    val calories: Int,
    val ingredients: List<String> = emptyList()
)

data class DayMeals(
    val day: String,
    val breakfast: MutableState<String> = mutableStateOf("None"),
    val lunch: MutableState<String> = mutableStateOf("None"),
    val dinner: MutableState<String> = mutableStateOf("None")
)

val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

// -------------------- Global state --------------------
val testMeals = mutableStateListOf<MealData>()
val weekMeals = mutableStateListOf<DayMeals>()

// -------------------- Main Activity --------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved recipes
        lifecycleScope.launch {
            MealStorage.loadRecipes(this@MainActivity).collectLatest { recipes ->
                testMeals.clear()
                testMeals.addAll(
                    recipes.ifEmpty {
                        listOf(
                            MealData(
                                "Pancakes",
                                "Fluffy pancakes with syrup and butter",
                                350,
                                listOf("Flour", "Eggs", "Milk", "Butter", "Syrup")
                            ),
                            MealData(
                                "Caesar Salad",
                                "Crisp romaine lettuce with Caesar dressing",
                                250,
                                listOf("Romaine Lettuce", "Caesar Dressing", "Croutons", "Parmesan Cheese")
                            ),
                            MealData(
                                "Spaghetti Bolognese",
                                "Spaghetti with tomato meat sauce",
                                500,
                                listOf("Spaghetti", "Ground Beef", "Tomato Sauce", "Onion", "Garlic")
                            )
                        )
                    }
                )
            }
        }

        // Load saved week meals
        lifecycleScope.launch {
            MealStorage.loadWeekMeals(this@MainActivity).collectLatest { savedWeek ->
                weekMeals.clear()
                weekMeals.addAll(savedWeek)
            }
        }

        setContent {
            MaterialTheme { // Use MaterialTheme instead of missing MealPlannerTheme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MealPlannerAppWithBottomBar(activity = this)
                }
            }
        }
    }
}

// -------------------- Main App --------------------
@Composable
fun MealPlannerAppWithBottomBar(activity: ComponentActivity) {
    var showShoppingCart by remember { mutableStateOf(false) }
    var showAddRecipe by remember { mutableStateOf(false) }
    var showClearWeekConfirm by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            BottomMenuBar(
                onAddRecipeClick = { showAddRecipe = true },
                onShoppingCartClick = { showShoppingCart = true },
                onClearWeekClick = { showClearWeekConfirm = true }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(weekMeals) { dayMeals ->
                Day(dayMeals = dayMeals, activity = activity)
            }
        }

        if (showShoppingCart) {
            ShoppingCartDialog(onDismiss = { showShoppingCart = false })
        }

        if (showAddRecipe) {
            AddRecipeDialog(
                activity = activity,
                onDismiss = { showAddRecipe = false }
            )
        }

        if (showClearWeekConfirm) {
            ClearWeekDialog(
                activity = activity,
                onConfirm = {
                    weekMeals.forEach { day ->
                        day.breakfast.value = "None"
                        day.lunch.value = "None"
                        day.dinner.value = "None"
                    }
                    activity.lifecycleScope.launch { MealStorage.saveWeekMeals(activity, weekMeals) }
                    showClearWeekConfirm = false
                },
                onDismiss = { showClearWeekConfirm = false }
            )
        }
    }
}

// -------------------- Bottom Menu --------------------
@Composable
fun BottomMenuBar(
    onAddRecipeClick: () -> Unit,
    onShoppingCartClick: () -> Unit,
    onClearWeekClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = onAddRecipeClick) { Text("Add Recipe") }
        Button(onClick = onShoppingCartClick) { Text("Shopping Cart") }
        Button(onClick = onClearWeekClick) { Text("Clear Week") }
    }
}

// -------------------- Add Recipe Dialog --------------------
@Composable
fun AddRecipeDialog(activity: ComponentActivity, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var ingredientsText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Recipe") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Meal Name") })
                OutlinedTextField(description, { description = it }, label = { Text("Description") })
                OutlinedTextField(calories, { calories = it }, label = { Text("Calories") }, singleLine = true)
                OutlinedTextField(ingredientsText, { ingredientsText = it }, label = { Text("Ingredients (comma separated)") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val cal = calories.toIntOrNull() ?: 0
                val ingredients = ingredientsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (name.isNotBlank()) {
                    testMeals.add(MealData(name, description, cal, ingredients))
                    activity.lifecycleScope.launch { MealStorage.saveRecipes(activity, testMeals) }
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
    )
}

// -------------------- Shopping Cart --------------------
@Composable
fun ShoppingCartDialog(onDismiss: () -> Unit) {
    val ingredients = weekMeals.flatMap { day ->
        listOf(day.breakfast.value, day.lunch.value, day.dinner.value)
            .filter { it != "None" }
            .mapNotNull { mealName -> testMeals.find { it.name == mealName }?.ingredients }
            .flatten()
    }.distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shopping Cart") },
        text = {
            if (ingredients.isEmpty()) Text("No meals selected this week.")
            else Column { ingredients.forEach { Text("• $it") } }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } }
    )
}

// -------------------- Clear Week --------------------
@Composable
fun ClearWeekDialog(activity: ComponentActivity, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var sliderPosition by remember { mutableStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear Week") },
        text = {
            Column {
                Text("Are you sure you want to clear all meals for the week?")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Slide to confirm")
                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = { onConfirm() },
                        enabled = sliderPosition >= 1f,
                        modifier = Modifier.weight(1f)
                    ) { Text("Confirm") }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

// -------------------- Day Composable --------------------
@Composable
fun Day(dayMeals: DayMeals, activity: ComponentActivity) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(dayMeals.day, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Meal("Breakfast", dayMeals.breakfast, activity)
        Meal("Lunch", dayMeals.lunch, activity)
        Meal("Dinner", dayMeals.dinner, activity)
    }
}

// -------------------- Meal Composable --------------------
@Composable
fun Meal(mealType: String, selectedMeal: MutableState<String>, activity: ComponentActivity) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("$mealType:", fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Button(onClick = { expanded = true }) { Text(selectedMeal.value) }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            testMeals.forEach { meal ->
                DropdownMenuItem(text = { Text(meal.name) }, onClick = {
                    selectedMeal.value = meal.name
                    expanded = false
                    activity.lifecycleScope.launch { MealStorage.saveWeekMeals(activity, weekMeals) }
                })
            }
        }

        if (selectedMeal.value != "None") {
            val mealData = testMeals.find { it.name == selectedMeal.value }
            mealData?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Description: ${it.description}", fontSize = 12.sp)
                Text("Calories: ${it.calories}", fontSize = 12.sp)
                Text("Ingredients: ${it.ingredients.joinToString(", ")}", fontSize = 12.sp)
            }
        }
    }
}
