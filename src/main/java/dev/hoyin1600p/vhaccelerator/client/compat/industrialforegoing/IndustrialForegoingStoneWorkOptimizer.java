package dev.hoyin1600p.vhaccelerator.client.compat.industrialforegoing;

import com.buuz135.industrial.block.resourceproduction.tile.MaterialStoneWorkFactoryTile;
import com.buuz135.industrial.block.resourceproduction.tile.MaterialStoneWorkFactoryTile.StoneWorkAction;
import com.buuz135.industrial.plugin.jei.JEICustomPlugin;
import com.buuz135.industrial.plugin.jei.category.StoneWorkCategory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class IndustrialForegoingStoneWorkOptimizer {
    private static final int MAX_ACTIONS = 4;

    private IndustrialForegoingStoneWorkOptimizer() {
    }

    public static List<StoneWorkCategory.Wrapper> findShortestOutputs(
            JEICustomPlugin plugin,
            ItemStack parent
    ) {
        Map<Item, Candidate> shortestByItem = new IdentityHashMap<>();
        int[] visitOrder = {0};
        visit(
                plugin,
                parent,
                parent,
                new ArrayList<>(),
                shortestByItem,
                visitOrder
        );

        return shortestByItem.values().stream()
                .sorted(Comparator.comparingInt(Candidate::visitOrder))
                .map(Candidate::wrapper)
                .toList();
    }

    private static void visit(
            JEICustomPlugin plugin,
            ItemStack parent,
            ItemStack current,
            List<StoneWorkAction> usedActions,
            Map<Item, Candidate> shortestByItem,
            int[] visitOrder
    ) {
        if (usedActions.size() >= MAX_ACTIONS) {
            return;
        }

        for (StoneWorkAction action
                : MaterialStoneWorkFactoryTile.ACTION_RECIPES) {
            if ("none".equals(action.getAction())) {
                continue;
            }

            ItemStack output = plugin.getStoneWorkOutputFrom(current, action);
            if (output.isEmpty()) {
                continue;
            }

            ArrayList<StoneWorkAction> nextActions =
                    new ArrayList<>(usedActions);
            nextActions.add(action);
            int outputVisitOrder = visitOrder[0]++;
            Item outputItem = output.getItem();
            Candidate existing = shortestByItem.get(outputItem);
            if (existing == null || nextActions.size() < existing.depth()) {
                shortestByItem.put(
                        outputItem,
                        new Candidate(
                                nextActions.size(),
                                outputVisitOrder,
                                new StoneWorkCategory.Wrapper(
                                        parent,
                                        nextActions,
                                        output.copy()
                                )
                        )
                );
            }

            visit(
                    plugin,
                    parent,
                    output,
                    nextActions,
                    shortestByItem,
                    visitOrder
            );
        }
    }

    private record Candidate(
            int depth,
            int visitOrder,
            StoneWorkCategory.Wrapper wrapper
    ) {
    }
}
