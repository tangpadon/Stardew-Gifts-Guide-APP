package com.example.svgifts;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final List<StardewItem> allItems = new ArrayList<>();
    private final List<NPCTaste> allNPCs = new ArrayList<>();
    private final List<String> universalLoves = new ArrayList<>();
    private final List<String> universalLikes = new ArrayList<>();
    
    // Use Set for faster lookup O(1)
    private final java.util.Set<String> springIds = new java.util.HashSet<>();
    private final java.util.Set<String> summerIds = new java.util.HashSet<>();
    private final java.util.Set<String> fallIds = new java.util.HashSet<>();
    private final java.util.Set<String> winterIds = new java.util.HashSet<>();
    AutoCompleteTextView autoCompleteSearch;
    TextView txtLoveResults, txtLikeResults;
    ImageView imgDisplay;
    Button btnCheck;
    SwitchCompat swExpanded;
    Object selectedObject = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Var to UI
        autoCompleteSearch = findViewById(R.id.autoCompleteSearch);
        txtLoveResults = findViewById(R.id.txtLoveResults);
        txtLikeResults = findViewById(R.id.txtLikeResults);
        imgDisplay = findViewById(R.id.imgDisplay);
        btnCheck = findViewById(R.id.btnCheck);
        swExpanded = findViewById(R.id.swExpanded);

        // loadData
        loadGameData();
        setupAutoComplete();

        // On/Off Expanded
        swExpanded.setOnCheckedChangeListener((buttonView, isChecked) -> {
            loadGameData();
            setupAutoComplete();
            autoCompleteSearch.setText("");
            selectedObject = null;
        });

        // 4. When Check Gifts
        btnCheck.setOnClickListener(v -> {
            String query = autoCompleteSearch.getText().toString().trim();
            if (query.isEmpty()) return;

            // If nothing is selected from dropdown, try to find by name
            if (selectedObject == null) {
                for (StardewItem item : allItems) {
                    if (item.getName().equalsIgnoreCase(query)) {
                        selectedObject = item;
                        break;
                    }
                }
                if (selectedObject == null) {
                    for (NPCTaste npc : allNPCs) {
                        if (npc.getNpcName().equalsIgnoreCase(query)) {
                            selectedObject = npc;
                            break;
                        }
                    }
                }
            }

            if (selectedObject instanceof StardewItem) {
                StardewItem item = (StardewItem) selectedObject;
                updateUIResults(item.getId(), item.getName());
                displayImage("items/" + item.getId() + ".png", item.getImageBase64());
            } else if (selectedObject instanceof NPCTaste) {
                NPCTaste npc = (NPCTaste) selectedObject;
                updateNPCUIResults(npc);
                displayImage("portraits/" + npc.getNpcName() + ".png", null);
            }
            
            // Clear selected object after use if it was manually found to avoid sticking
            selectedObject = null;
        });
    }

    private void setupAutoComplete() {
        List<Object> combinedList = new ArrayList<>();
        combinedList.addAll(allItems);
        combinedList.addAll(allNPCs);

        // Sort A-Z by name/npcName
        combinedList.sort((o1, o2) -> {
            String name1 = o1.toString();
            String name2 = o2.toString();
            return name1.compareToIgnoreCase(name2);
        });

        ArrayAdapter<Object> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, combinedList);
        autoCompleteSearch.setAdapter(adapter);

        autoCompleteSearch.setOnItemClickListener((parent, view, position, id) -> {
            selectedObject = parent.getItemAtPosition(position);
        });

        autoCompleteSearch.setOnClickListener(v -> {
            autoCompleteSearch.setText("");
            selectedObject = null;
        });
    }

    private void displayImage(String path, String base64Data) {
        try {
            // 1. Try Base64 first
            if (base64Data != null && !base64Data.isEmpty()) {
                byte[] decodedString = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                if (decodedByte != null) {
                    imgDisplay.setImageBitmap(decodedByte);
                    imgDisplay.setVisibility(android.view.View.VISIBLE);
                    return;
                }
            }

            // 2. Fallback to local assets with robust resolution
            String filename = path.contains("/") ? path.substring(path.lastIndexOf("/") + 1) : path;
            String folder = path.contains("/") ? path.substring(0, path.lastIndexOf("/") + 1) : "items/";
            
            // Try in order: Exact Path -> Clean ID -> Lowercase Clean ID
            String cleanName = StardewItem.sanitizeId(filename.replace(".png", "")) + ".png";
            String[] attempts = { path, folder + cleanName, (folder + cleanName).toLowerCase() };

            for (String attempt : attempts) {
                try (InputStream is = getAssets().open(attempt)) {
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    if (bitmap != null) {
                        // Portrait Cropping (64x64)
                        if (attempt.startsWith("portraits/") && bitmap.getWidth() >= 64 && bitmap.getHeight() >= 64) {
                            bitmap = Bitmap.createBitmap(bitmap, 0, 0, 64, 64);
                        }
                        imgDisplay.setImageBitmap(bitmap);
                        imgDisplay.setVisibility(android.view.View.VISIBLE);
                        return;
                    }
                } catch (IOException ignored) {}
            }

            imgDisplay.setVisibility(android.view.View.GONE);
            Log.w("StardewTest", "Image not found: " + path);
        } catch (Exception e) {
            imgDisplay.setVisibility(android.view.View.GONE);
            Log.e("StardewTest", "Error loading image: " + e.getMessage());
        }
    }

    private void updateNPCUIResults(NPCTaste npc) {

        // Universal
        List<String> loveIds = new ArrayList<>(universalLoves);
        List<String> likeIds = new ArrayList<>(universalLikes);

        // NPC
        for (String id : npc.getLoveIDs()) {
            if (!loveIds.contains(id)) loveIds.add(id);
        }
        for (String id : npc.getLikeIDs()) {
            if (!likeIds.contains(id)) likeIds.add(id);
        }

        txtLoveResults.setText(formatSeasonalItemList(loveIds));
        txtLikeResults.setText(formatSeasonalItemList(likeIds));
    }

    private CharSequence formatSeasonalItemList(List<String> itemIds) {
        if (itemIds.isEmpty()) return "None";

        // Sort itemIds by their actual names A-Z
        itemIds.sort((id1, id2) -> {
            String name1 = getDisplayName(id1);
            String name2 = getDisplayName(id2);
            return name1.compareToIgnoreCase(name2);
        });

        List<String> tempSpring = new ArrayList<>();
        List<String> tempSummer = new ArrayList<>();
        List<String> tempFall = new ArrayList<>();
        List<String> tempWinter = new ArrayList<>();
        List<String> otherIds = new ArrayList<>();

        for (String id : itemIds) {
            StardewItem item = getItemById(id);
            if (item != null) {
                String cleanId = item.getCleanId();
                String rawId = item.getId();
                
                boolean inSpring = this.springIds.contains(rawId) || this.springIds.contains(cleanId);
                boolean inSummer = this.summerIds.contains(rawId) || this.summerIds.contains(cleanId);
                boolean inFall = this.fallIds.contains(rawId) || this.fallIds.contains(cleanId);
                boolean inWinter = this.winterIds.contains(rawId) || this.winterIds.contains(cleanId);

                if (inSpring && inSummer && inFall && inWinter) {
                    otherIds.add(id);
                } else {
                    boolean categorized = false;
                    if (inSpring) {
                        tempSpring.add(id);
                        categorized = true;
                    }
                    if (inSummer) {
                        tempSummer.add(id);
                        categorized = true;
                    }
                    if (inFall) {
                        tempFall.add(id);
                        categorized = true;
                    }
                    if (inWinter) {
                        tempWinter.add(id);
                        categorized = true;
                    }

                    if (!categorized) {
                        otherIds.add(id);
                    }
                }
            } else {
                // Category IDs or missing items go to Other
                otherIds.add(id);
            }
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        
        // Spring Section
        if (!tempSpring.isEmpty()) {
            appendSeasonalSection(ssb, "Spring Available:", 0xFFE91E63, tempSpring);
        }

        // Summer Section
        if (!tempSummer.isEmpty()) {
            if (ssb.length() > 0) ssb.append("\n\n");
            appendSeasonalSection(ssb, "Summer Available:", 0xFFFF9800, tempSummer);
        }

        // Fall Section
        if (!tempFall.isEmpty()) {
            if (ssb.length() > 0) ssb.append("\n\n");
            appendSeasonalSection(ssb, "Fall Available:", 0xFF795548, tempFall);
        }

        // Winter Section
        if (!tempWinter.isEmpty()) {
            if (ssb.length() > 0) ssb.append("\n\n");
            appendSeasonalSection(ssb, "Winter Available:", 0xFF2196F3, tempWinter);
        }

        // Other Section
        if (!otherIds.isEmpty()) {
            if (ssb.length() > 0) ssb.append("\n\n");
            int start = ssb.length();
            ssb.append("Other:\n");
            ssb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append(formatItemList(otherIds));
        }

        return ssb;
    }

    private void appendSeasonalSection(SpannableStringBuilder ssb, String title, int color, List<String> ids) {
        int start = ssb.length();
        ssb.append(title).append("\n");
        ssb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new android.text.style.ForegroundColorSpan(color), start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.append(formatItemList(ids));
    }

    private CharSequence formatItemList(List<String> itemIds) {
        if (itemIds.isEmpty()) return "None";
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        int size = (int) (txtLoveResults.getTextSize() * 1.1);

        for (int i = 0; i < itemIds.size(); i++) {
            String id = itemIds.get(i);
            StardewItem item = getItemById(id);

            int start = ssb.length();
            if (item != null) {
                ssb.append(" "); // Placeholder for icon
                Drawable d = getItemDrawable(item);
                if (d != null) {
                    d.setBounds(0, 0, size, size);
                    ssb.setSpan(new ImageSpan(d, ImageSpan.ALIGN_BASELINE), start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                ssb.append("\u00A0"); // Non-breaking space
                ssb.append(item.getName());
            } else {
                ssb.append(getDisplayName(id));
            }

            if (i < itemIds.size() - 1) {
                ssb.append("\t");
            }
        }
        return ssb;
    }

    private CharSequence formatNpcList(List<String> npcNames) {
        if (npcNames.isEmpty()) return "None";

        // Sort NPC names A-Z
        npcNames.sort(String::compareToIgnoreCase);

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        int size = (int) (txtLoveResults.getTextSize() * 1.1);

        for (int i = 0; i < npcNames.size(); i++) {
            String name = npcNames.get(i);
            int start = ssb.length();
            ssb.append(" "); // Placeholder for icon
            Drawable d = getPortraitDrawable(name);
            if (d != null) {
                d.setBounds(0, 0, size, size);
                ssb.setSpan(new ImageSpan(d, ImageSpan.ALIGN_BASELINE), start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            ssb.append("\u00A0"); // Non-breaking space to glue icon to text
            ssb.append(name);

            if (i < npcNames.size() - 1) {
                ssb.append("\t"); // Normal spaces here to allow wrap between items
            }
        }
        return ssb;
    }

    private Drawable getPortraitDrawable(String npcName) {
        try (InputStream is = getAssets().open("portraits/" + npcName + ".png")) {
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap != null && bitmap.getWidth() >= 64 && bitmap.getHeight() >= 64) {
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, 64, 64);
                return new BitmapDrawable(getResources(), bitmap);
            }
            return (bitmap != null) ? new BitmapDrawable(getResources(), bitmap) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private Drawable getItemDrawable(StardewItem item) {
        if (item == null) return null;
        if (item.getImageBase64() != null && !item.getImageBase64().isEmpty()) {
            try {
                byte[] decodedString = android.util.Base64.decode(item.getImageBase64(), android.util.Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                if (decodedByte != null) {
                    return new BitmapDrawable(getResources(), decodedByte);
                }
            } catch (Exception ignored) {
                // Silently fail for Base64 and fall back to assets
            }
        }
        
        // Fallback to local asset if base64 fails or is missing
        String id = item.getId();
        String cleanId = item.getCleanId();
        String[] paths = {
            "items/" + id + ".png",
            "items/" + cleanId + ".png",
            "items/" + cleanId.toLowerCase() + ".png"
        };

        for (String path : paths) {
            try (InputStream is = getAssets().open(path)) {
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (bitmap != null) {
                    return new BitmapDrawable(getResources(), bitmap);
                }
            } catch (IOException ignored) {}
        }
        return null;
    }

    private StardewItem getItemById(String id) {
        if (id == null) return null;
        String targetCleanId = StardewItem.sanitizeId(id);
        
        for (StardewItem item : allItems) {
            if (item.getId().equalsIgnoreCase(id) || item.getCleanId().equalsIgnoreCase(targetCleanId)) {
                return item;
            }
        }
        return null;
    }

    private String getDisplayName(String id) {
        StardewItem item = getItemById(id);
        if (item != null) return item.getName();

        // Handle common Stardew categories and special tags
        switch (id) {
            case "-2": return "Artisan Goods";
            case "-4": return "Fish";
            case "-5": return "Eggs";
            case "-6": return "Milk";
            case "-7": return "Cooking";
            case "-8": return "Artifacts";
            case "-12": return "Minerals";
            case "-15": return "Metal Ores";
            case "-16": return "Building Resources";
            case "-19": return "Fertilizers";
            case "-20": return "Trash";
            case "-24": return "Furniture";
            case "-26": return "Resources";
            case "-27": return "Tackle";
            case "-28": return "Monster Loot";
            case "-74": return "Seeds";
            case "-75": return "Vegetables";
            case "-79": return "Fruits";
            case "-80": return "Flowers";
            case "-81": return "Foraging";
            case "book_item": return "Books";
            case "category_trinket": return "Trinkets";
            case "edible_mushroom": return "Mushrooms";
            case "forage_item_beach": return "Beach Forage";
            case "ancient_item": return "Ancient Items";
            case "doll_item": return "Dolls";
            case "toy_item": return "Toys";
            case "archaeological_item": return "Artifacts";
            default: return id;
        }
    }

    private void updateUIResults(String itemId, String itemName) {
        String cleanSearchId = StardewItem.sanitizeId(itemId);
        
        // Find the item to get its categoryId
        String itemCategoryId = "";
        for (StardewItem item : allItems) {
            if (item.getId().equals(itemId)) {
                itemCategoryId = item.getCategoryId();
                break;
            }
        }

        List<String> lovers = new ArrayList<>();
        List<String> likers = new ArrayList<>();

        // 1. Check Universals
        boolean isUniversalLove = universalLoves.contains(cleanSearchId) || (!itemCategoryId.isEmpty() && universalLoves.contains(itemCategoryId));
        boolean isUniversalLike = universalLikes.contains(cleanSearchId) || (!itemCategoryId.isEmpty() && universalLikes.contains(itemCategoryId));

        for (NPCTaste npc : allNPCs) {
            boolean isSpecificLove = false;
            boolean isSpecificLike = false;

            // 2. Check specific NPC tastes
            for (String npcLoveId : npc.getLoveIDs()) {
                if (npcLoveId.equalsIgnoreCase(itemId) || npcLoveId.equalsIgnoreCase(cleanSearchId) || (!itemCategoryId.isEmpty() && npcLoveId.equalsIgnoreCase(itemCategoryId))) {
                    isSpecificLove = true;
                    break;
                }
            }

            // Only check Likes if it wasn't a Love
            if (!isSpecificLove) {
                for (String npcLikeId : npc.getLikeIDs()) {
                    if (npcLikeId.equalsIgnoreCase(itemId) || npcLikeId.equalsIgnoreCase(cleanSearchId) || (!itemCategoryId.isEmpty() && npcLikeId.equalsIgnoreCase(itemCategoryId))) {
                        isSpecificLike = true;
                        break;
                    }
                }
            }

            // 3. Assignment Logic (Specifics override Universals)
            if (isSpecificLove) {
                lovers.add(npc.getNpcName());
            } else if (isSpecificLike) {
                likers.add(npc.getNpcName());
            } else if (isUniversalLove) {
                lovers.add(npc.getNpcName());
            } else if (isUniversalLike) {
                likers.add(npc.getNpcName());
            }
        }
        
        // Remove duplicates in case they were added multiple times (e.g. Universal + Specific)
        List<String> uniqueLovers = new ArrayList<>(new java.util.LinkedHashSet<>(lovers));
        List<String> uniqueLikers = new ArrayList<>(new java.util.LinkedHashSet<>(likers));

        txtLoveResults.setText(formatNpcList(uniqueLovers));
        txtLikeResults.setText(formatNpcList(uniqueLikers));
    }

    private void loadGameData() {
        try {
            allItems.clear();
            allNPCs.clear();
            universalLoves.clear();
            universalLikes.clear();
            springIds.clear();
            summerIds.clear();
            fallIds.clear();
            winterIds.clear();

            // 1. Load Items
            loadItems("objects.json");
            if (swExpanded != null && swExpanded.isChecked()) {
                loadItems("sve_objects.json");
            }

            // 2. Load NPC Gifts
            loadGifts("NPCGiftTastes.json");
            if (swExpanded != null && swExpanded.isChecked()) {
                loadGifts("sve_NPCGiftTastes.json");
            }

            Log.d("SVGifts", "Loaded items: " + allItems.size());
            Log.d("SVGifts", "Loaded NPCs: " + allNPCs.size());

        } catch (Exception e) {
            Log.e("SVGifts", "Error loading data: " + e.getMessage(), e);
        }
    }

    private void loadItems(String fileName) throws Exception {
        String jsonString = loadJSONFromAsset(fileName);
        if (jsonString == null) return;

        JSONArray objectsArray = new JSONArray(jsonString);

        for (int i = 0; i < objectsArray.length(); i++) {
            JSONObject itemDetails = objectsArray.getJSONObject(i);

            String id = itemDetails.getString("id");
            String imageBase64 = itemDetails.optString("image", null);
            String name = itemDetails.optString("name", "Unknown Item");
            String categoryId = itemDetails.optString("category", "");

            // If the image field contains a filename (e.g. "Item.png") instead of Base64, set it to null
            // so the app correctly falls back to loading from the assets folder.
            // Heuristic: Base64 strings are typically long and don't contain spaces or end in .png.
            // Modded JSONs often put the filename in the "image" field.
            if (imageBase64.toLowerCase().endsWith(".png") || imageBase64.contains(" ") || imageBase64.length() < 64) {
                imageBase64 = null;
            }

            // add to List
            allItems.add(new StardewItem(id, name, imageBase64, categoryId));

            JSONArray seasons = itemDetails.optJSONArray("seasons");
            if (seasons != null) {
                for (int j = 0; j < seasons.length(); j++) {
                    String season = seasons.getString(j);
                    String cleanId = StardewItem.sanitizeId(id);
                    if (season.equalsIgnoreCase("spring")) {
                        springIds.add(id);
                        springIds.add(cleanId);
                    } else if (season.equalsIgnoreCase("summer")) {
                        summerIds.add(id);
                        summerIds.add(cleanId);
                    } else if (season.equalsIgnoreCase("fall")) {
                        fallIds.add(id);
                        fallIds.add(cleanId);
                    } else if (season.equalsIgnoreCase("winter")) {
                        winterIds.add(id);
                        winterIds.add(cleanId);
                    }
                }
            }
        }
    }

    private void loadGifts(String fileName) throws Exception {
        String jsonString = loadJSONFromAsset(fileName);
        if (jsonString == null) return;

        JSONObject root = new JSONObject(jsonString);
        JSONObject content = root.getJSONObject("content");

        if (content.has("Universal_Love")) {
            String[] ids = content.getString("Universal_Love").split(" ");
            for (String id : ids) {
                if (!id.trim().isEmpty()) {
                    universalLoves.add(StardewItem.sanitizeId(id));
                }
            }
        }
        if (content.has("Universal_Like")) {
            String[] ids = content.getString("Universal_Like").split(" ");
            for (String id : ids) {
                if (!id.trim().isEmpty()) {
                    universalLikes.add(StardewItem.sanitizeId(id));
                }
            }
        }

        Iterator<String> keys = content.keys();
        while (keys.hasNext()) {
            String npcName = keys.next();
            if (npcName.startsWith("Universal_")) continue;

            String rawData = content.getString(npcName);
            
            boolean found = false;
            for (NPCTaste existing : allNPCs) {
                if (existing.getNpcName().equalsIgnoreCase(npcName)) {
                    existing.appendData(rawData);
                    found = true;
                    break;
                }
            }
            if (!found) {
                allNPCs.add(new NPCTaste(npcName, rawData));
            }
        }
    }

    // read assets folder
    private String loadJSONFromAsset(String fileName) {
        try (InputStream is = getAssets().open(fileName)) {
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            Log.e("SVGifts", "Error reading asset: " + fileName, ex);
            return null;
        }
    }

}
