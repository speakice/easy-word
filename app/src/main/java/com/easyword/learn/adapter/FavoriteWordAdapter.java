package com.easyword.learn.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.easyword.learn.R;
import com.easyword.learn.data.Word;
import com.easyword.learn.databinding.ItemFavoriteWordBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 收藏本页的适配器。
 *
 * <p>单击条目跳回滑动页对应字；长按条目取消收藏。</p>
 */
public class FavoriteWordAdapter extends RecyclerView.Adapter<FavoriteWordAdapter.FavoriteViewHolder> {

    /** 条目交互回调，由 FavoriteActivity 实现。 */
    public interface OnFavoriteWordAction {
        /** 单击：返回卡片定位该字。 */
        void onWordClick(Word word);

        /** 长按：取消收藏。 */
        void onWordLongClick(Word word);
    }

    private final List<Word> words = new ArrayList<>();
    private final OnFavoriteWordAction listener;

    public FavoriteWordAdapter(OnFavoriteWordAction listener) {
        this.listener = listener;
    }

    /** 更新列表数据。 */
    public void submitList(List<Word> newWords) {
        words.clear();
        if (newWords != null) {
            words.addAll(newWords);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FavoriteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFavoriteWordBinding binding =
                ItemFavoriteWordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        FavoriteViewHolder holder = new FavoriteViewHolder(binding);
        binding.getRoot().setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onWordClick(words.get(pos));
            }
        });
        binding.getRoot().setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onWordLongClick(words.get(pos));
                return true;
            }
            return false;
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull FavoriteViewHolder holder, int position) {
        Word word = words.get(position);
        holder.binding.textFavoriteWord.setText(word.getWord());
        holder.binding.textFavoritePinyin.setText(word.getPinyin());
    }

    @Override
    public int getItemCount() {
        return words.size();
    }

    /** Item ViewHolder。 */
    static class FavoriteViewHolder extends RecyclerView.ViewHolder {
        final ItemFavoriteWordBinding binding;

        FavoriteViewHolder(@NonNull ItemFavoriteWordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}