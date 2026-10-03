package com.example.svgifts;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class NPCTaste {
    private final String npcName;
    private final List<String> loveIDs;
    private final List<String> likeIDs;

    public NPCTaste(String name, String rawDataFromJSON) {
        this.npcName = name;
        this.loveIDs = new ArrayList<>();
        this.likeIDs = new ArrayList<>();
        appendData(rawDataFromJSON);
    }

    public void appendData(String rawDataFromJSON) {
        String[] parts = rawDataFromJSON.split("/");

        // SVE NPCs have a different structure: {{i18n:...}}/ID ID ID
        // Vanilla NPCs have: ID ID ID (Indices are different if we split by /)
        int loveIndex = 1;
        int likeIndex = 3;

        // Check for edge cases where the first part is empty (starts with /)
        if (rawDataFromJSON.startsWith("/")) {
            loveIndex = 1;
            likeIndex = 3;
        }


        // Append Love IDs
        if (parts.length > loveIndex) {
            String[] ids = parts[loveIndex].trim().split(" ");
            for (String id : ids) {
                if (id.isEmpty()) continue;
                String cleanId = sanitizeId(id);
                if (!cleanId.isEmpty() && !this.loveIDs.contains(cleanId)) {
                    this.loveIDs.add(cleanId);
                }
            }
        }

        // Append Like IDs
        if (parts.length > likeIndex) {
            String[] ids = parts[likeIndex].trim().split(" ");
            for (String id : ids) {
                if (id.isEmpty()) continue;
                String cleanId = sanitizeId(id);
                if (!cleanId.isEmpty() && !this.likeIDs.contains(cleanId)) {
                    this.likeIDs.add(cleanId);
                }
            }
        }
    }

    private String sanitizeId(String id) {
        return StardewItem.sanitizeId(id);
    }

    public String getNpcName() { return npcName; }
    public List<String> getLoveIDs() { return loveIDs; }
    public List<String> getLikeIDs() { return likeIDs; }

    @NonNull
    @Override
    public String toString() {
        return npcName;
    }
}
