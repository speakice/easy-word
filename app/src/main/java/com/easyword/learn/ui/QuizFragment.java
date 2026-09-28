package com.easyword.learn.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.easyword.learn.adapter.TestListAdapter;
import com.easyword.learn.data.TestCatalog;
import com.easyword.learn.databinding.FragmentQuizBinding;
import com.easyword.learn.viewmodel.WordViewModel;

/**
 * 测验 Tab：考试目录。每 100 字（一批）一次单元测试 = 一个年级，
 * 学满 600 字有小学毕业考试，学完 950 字有初中毕业考试。
 */
public class QuizFragment extends Fragment {

    private FragmentQuizBinding binding;
    private WordViewModel viewModel;
    private TestListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentQuizBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(WordViewModel.class);
        adapter = new TestListAdapter(spec ->
                startActivity(TestActivity.intent(requireContext(), spec.id)));
        binding.testList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.testList.setAdapter(adapter);

        viewModel.getStats().observe(getViewLifecycleOwner(), stats -> {
            if (stats != null) {
                adapter.setData(TestCatalog.ALL, stats.batchPercent);
            }
        });
        viewModel.init();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 考完试回来重新读一次成绩，列表上的“最好 XX 分”要立刻更新
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
}
