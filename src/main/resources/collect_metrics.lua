function collect_metrics()
    if game.tick_paused then
        return '{"paused": true}'
    end
    local output_data = game.forces["player"].item_production_statistics.output_counts
    local input_data = game.forces["player"].item_production_statistics.input_counts
    local gameTick = game.ticks_played
    inputJson = '{'
    for key, value in pairs(input_data) do
        if #inputJson > 1 then
            inputJson = inputJson .. ','
        end
        inputJson = inputJson .. '"' .. key .. '": ' .. value
    end
    inputJson = inputJson .. '}'
    outputJson = '{'
    for key, value in pairs(output_data) do
        if #outputJson > 1 then
            outputJson = outputJson .. ','
        end
        outputJson = outputJson .. '"' .. key .. '": ' .. value
    end
    outputJson = outputJson .. '}'
    return '{' ..
                '"gameTick":' .. gameTick .. ',' ..
                '"itemsConsumed":' .. inputJson .. ',' ..
                '"itemsProduced":' .. outputJson ..
            '}'
end