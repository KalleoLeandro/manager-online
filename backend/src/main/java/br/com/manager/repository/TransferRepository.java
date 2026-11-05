package br.com.manager.repository;

import br.com.manager.models.dto.FinanceDtos.*;
import br.com.manager.models.requests.TransferOfferRequest;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class TransferRepository {

    private final NamedParameterJdbcTemplate db;

    public TransferRepository(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    private static final String SELECT =
        "SELECT o.*,p.name AS player_name,b.name AS buyer_name,s.name AS seller_name FROM transfer_offers o JOIN save_players p ON p.save_id=o.save_id AND p.player_id=o.player_id JOIN save_teams b ON b.save_id=o.save_id AND b.team_id=o.buyer_id LEFT JOIN save_teams s ON s.save_id=o.save_id AND s.team_id=o.seller_id ";
    private static final RowMapper<Offer> OFFER = (r, n) ->
        new Offer(
            r.getLong("id"),
            r.getLong("player_id"),
            r.getString("player_name"),
            r.getLong("buyer_id"),
            r.getString("buyer_name"),
            r.getObject("seller_id", Long.class),
            r.getString("seller_name"),
            r.getLong("season_id"),
            r.getString("status"),
            r.getInt("club_meter"),
            r.getInt("player_meter"),
            r.getLong("fee"),
            r.getLong("salary_monthly"),
            r.getInt("months"),
            r.getLong("signing_bonus"),
            r.getLong("appearance_bonus"),
            r.getLong("goal_bonus"),
            r.getLong("title_bonus"),
            r.getObject("offered_date", LocalDate.class),
            r.getObject("decision_date", LocalDate.class),
            r.getObject("expires_date", LocalDate.class),
            r.getBoolean("seller_approved"),
            r.getString("feedback"),
            r.getInt("revision")
        );

    public Offer offer(String id, long offer) {
        return db
            .query(
                SELECT + "WHERE o.save_id=:id AND o.id=:offer",
                new MapSqlParameterSource("id", id).addValue("offer", offer),
                OFFER
            )
            .stream()
            .findFirst()
            .orElse(null);
    }

    public List<Offer> list(String id, long team) {
        return db.query(
            SELECT + "WHERE o.save_id=:id AND (o.buyer_id=:team OR o.seller_id=:team) ORDER BY o.id DESC LIMIT 100",
            new MapSqlParameterSource("id", id).addValue("team", team),
            OFFER
        );
    }

    public List<Offer> completed(String id) {
        return db.query(
            SELECT + "WHERE o.save_id=:id AND o.status='ACCEPTED' ORDER BY o.id DESC LIMIT 100",
            new MapSqlParameterSource("id", id),
            OFFER
        );
    }

    public List<Offer> pending(String id, long team) {
        return db.query(
            SELECT +
                "WHERE o.save_id=:id AND o.buyer_id=:team AND o.status IN ('CLUB_REVIEW','PENDING','NEGOTIABLE') ORDER BY o.id",
            new MapSqlParameterSource("id", id).addValue("team", team),
            OFFER
        );
    }

    public boolean duplicate(String id, long team, long player) {
        return (
            db.queryForObject(
                "SELECT COUNT(*) FROM transfer_offers WHERE save_id=:id AND buyer_id=:team AND player_id=:player AND status IN ('CLUB_REVIEW','PENDING','NEGOTIABLE')",
                new MapSqlParameterSource("id", id).addValue("team", team).addValue("player", player),
                Integer.class
            ) > 0
        );
    }

    private MapSqlParameterSource terms(String id, TransferOfferRequest r) {
        return new MapSqlParameterSource("id", id)
            .addValue("player", r.playerId())
            .addValue("fee", r.fee())
            .addValue("salary", r.salaryMonthly())
            .addValue("months", r.months())
            .addValue("signing", r.signingBonus())
            .addValue("appearance", r.appearanceBonus())
            .addValue("goal", r.goalBonus())
            .addValue("title", r.titleBonus());
    }

    public long create(
        String id,
        long buyer,
        Long seller,
        long season,
        TransferOfferRequest r,
        LocalDate date,
        boolean approved,
        int clubMeter,
        int playerMeter
    ) {
        var key = new GeneratedKeyHolder();
        db.update(
            "INSERT INTO transfer_offers(save_id,player_id,buyer_id,seller_id,season_id,status,club_meter,player_meter,fee,salary_monthly,months,signing_bonus,appearance_bonus,goal_bonus,title_bonus,offered_date,expires_date,seller_approved,feedback) VALUES(:id,:player,:buyer,:seller,:season,:status,:club,:interest,:fee,:salary,:months,:signing,:appearance,:goal,:title,:date,:expiry,:approved,:feedback)",
            terms(id, r)
                .addValue("buyer", buyer)
                .addValue("seller", seller)
                .addValue("season", season)
                .addValue("status", approved ? "PENDING" : "CLUB_REVIEW")
                .addValue("club", clubMeter)
                .addValue("interest", playerMeter)
                .addValue("date", date)
                .addValue("expiry", date.plusDays(14))
                .addValue("approved", approved)
                .addValue(
                    "feedback",
                    approved
                        ? "Proposta em avaliação pelo atleta."
                        : "Aguardando o treinador do clube vendedor autorizar a negociação."
                ),
            key,
            new String[] { "id" }
        );
        return key.getKey().longValue();
    }

    public void revise(String id, long offer, TransferOfferRequest r, LocalDate date, int club, int player) {
        db.update(
            "UPDATE transfer_offers SET fee=:fee,salary_monthly=:salary,months=:months,signing_bonus=:signing,appearance_bonus=:appearance,goal_bonus=:goal,title_bonus=:title,club_meter=:club,player_meter=:interest,status=CASE WHEN seller_approved THEN 'PENDING' ELSE 'CLUB_REVIEW' END,decision_date=NULL,expires_date=:expiry,revision=revision+1,feedback='Proposta melhorada, aguardando nova avaliação.' WHERE save_id=:id AND id=:offer",
            terms(id, r)
                .addValue("offer", offer)
                .addValue("club", club)
                .addValue("interest", player)
                .addValue("expiry", date.plusDays(14))
        );
    }

    public void sellerApproval(String id, long offer) {
        db.update(
            "UPDATE transfer_offers SET seller_approved=TRUE,club_meter=100,revision=revision+1 WHERE save_id=:id AND id=:offer",
            new MapSqlParameterSource("id", id).addValue("offer", offer)
        );
    }

    public void status(String id, long offer, String status, String feedback, LocalDate decision) {
        db.update(
            "UPDATE transfer_offers SET status=:status,feedback=:feedback,decision_date=:date,revision=revision+1 WHERE save_id=:id AND id=:offer",
            new MapSqlParameterSource("id", id)
                .addValue("offer", offer)
                .addValue("status", status)
                .addValue("feedback", feedback)
                .addValue("date", decision)
        );
    }

    public TransferOfferRequest terms(Offer o) {
        return new TransferOfferRequest(
            o.playerId(),
            o.fee(),
            o.salaryMonthly(),
            o.months(),
            o.signingBonus(),
            o.appearanceBonus(),
            o.goalBonus(),
            o.titleBonus()
        );
    }
}
