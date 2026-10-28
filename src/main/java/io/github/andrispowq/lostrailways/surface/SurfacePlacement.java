package io.github.andrispowq.lostrailways.surface;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure atomic-piece safety gate; validation completes against original terrain before writing. */
public final class SurfacePlacement {
    public enum Existing { AIR, VEGETATION, NATURAL_GROUND, SOLID, FLUID, PROTECTED }

    public interface Target {
        boolean canWrite(SurfacePiece.Cell cell);
        Existing existing(SurfacePiece.Cell cell);
        void write(SurfacePiece.Change change);
    }

    public record Result(int acceptedPieces, int rejectedPieces, int blocks) { }

    public Result place(List<SurfacePiece> pieces, Target target) {
        Map<SurfacePiece.Cell, SurfacePiece.Change> writes = new LinkedHashMap<>();
        int accepted = 0;
        List<SurfacePiece> ordered = new ArrayList<>(pieces);
        ordered.sort(Comparator.comparingInt(piece -> switch (piece.kind()) {
            case TRACK -> 0;
            case STATION -> 1;
            case JUNCTION -> 2;
        }));
        for (SurfacePiece piece : ordered) {
            boolean safe = piece.ground().stream().allMatch(cell -> target.canWrite(cell) && target.existing(cell) == Existing.NATURAL_GROUND)
                    && piece.clearance().stream().allMatch(cell -> target.canWrite(cell)
                    && (target.existing(cell) == Existing.AIR || target.existing(cell) == Existing.VEGETATION))
                    && piece.blocks().stream().allMatch(change -> target.canWrite(change.position())
                    && (piece.ground().contains(change.position()) || piece.clearance().contains(change.position()))
                    && (!writes.containsKey(change.position()) || writes.get(change.position()).equals(change)));
            if (safe) {
                accepted++;
                piece.blocks().forEach(change -> writes.put(change.position(), change));
            }
        }
        writes.values().forEach(target::write);
        return new Result(accepted, pieces.size() - accepted, writes.size());
    }
}
