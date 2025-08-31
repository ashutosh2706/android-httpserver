package com.android.httpserver;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.httpserver.model.History;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

public class HistoryBottomSheet extends BottomSheetDialogFragment {

    private RecyclerView recyclerView;
    private List<History> historyList;

    public HistoryBottomSheet(List<History> historyList) {
        this.historyList = historyList;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet, container, false);

        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        HistoryAdapter adapter = new HistoryAdapter(historyList);
        recyclerView.setAdapter(adapter);
        return view;
    }
}
