package com.android.httpserver.component;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.httpserver.R;
import com.android.httpserver.component.HistoryAdapter;
import com.android.httpserver.model.History;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

public class BottomSheet extends BottomSheetDialogFragment {

    private RecyclerView recyclerView;
    private List<History> historyList;
    private HistoryAdapter.OnDeleteClickListener deleteClickListener;

    public BottomSheet(List<History> historyList, HistoryAdapter.OnDeleteClickListener deleteClickListener) {
        this.historyList = historyList;
        this.deleteClickListener = deleteClickListener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet, container, false);

        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        HistoryAdapter adapter = new HistoryAdapter(deleteClickListener);
        adapter.setHistoryList(historyList);
        recyclerView.setAdapter(adapter);
        return view;
    }
}
