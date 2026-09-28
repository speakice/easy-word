package com.easyword.learn.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.easyword.learn.R;
import com.easyword.learn.data.Word;
import com.easyword.learn.databinding.ItemListWordBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 字表网格适配器：每行显示字 + 喇叭（点喇叭教读），点字回调进入学习页。
 */
public class WordListAdapter extends RecyclerView.Adapter<WordListAdapter.ItemHolder> {

    /** 列表行为回调。 */
    public interface OnListAction {
        /** 点字：进入该字表的学习页。 */
        void onWordClick(Word word);

        /** 点喇叭：教读。 */
        void onSpeak(Word word);
    }

    private final List<Word> words = new ArrayList<>();
    private final OnListAction listener;

    public WordListAdapter(OnListAction listener) {
        this.listener = listener;
    }

    public void submit(List<Word> list) {
        words.clear();
        if (list != null) {
            words.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemListWordBinding binding = ItemListWordBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        ItemHolder holder = new ItemHolder(binding);
        binding.getRoot().setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onWordClick(words.get(pos));
            }
        });
        binding.btnSpeak.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onSpeak(words.get(pos));
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ItemHolder holder, int position) {
        Word word = words.get(position);
        holder.binding.textChar.setText(word.getWord());
    }

    @Override
    public int getItemCount() {
        return words.size();
    }

    static class ItemHolder extends RecyclerView.ViewHolder {
        final ItemListWordBinding binding;

        ItemHolder(@NonNull ItemListWordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}