package com.android.httpserver.component;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.android.httpserver.data.AppDB;
import com.android.httpserver.data.HistoryDao;
import com.android.httpserver.model.History;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryViewModel extends AndroidViewModel {

    private final HistoryDao historyDao;
    private final LiveData<List<History>> allHistory;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public HistoryViewModel(@NonNull Application application) {
        super(application);
        AppDB db = AppDB.getInstance(application);
        historyDao = db.historyDao();
        allHistory = historyDao.getAllHistory();
    }

    public LiveData<List<History>> getAllHistory() {
        return allHistory;
    }

    public void insert(History history) {
        executorService.execute(() -> historyDao.insert(history));
    }

    public void delete(History history) {
        System.out.println(history.getFileName() + " request to delete");
//        executorService.execute(() -> historyDao.delete(history));
    }
}
